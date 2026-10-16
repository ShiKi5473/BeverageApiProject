package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshotEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionJpaRepository;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 盤點服務 — 負責庫存盤點 (Audit) 相關邏輯
 * 包含：執行盤點、取得盤點清單、盤盈處理、盤損委派扣減
 */
@Service
public class InventoryAuditService {

    private static final String REASON_TYPE_AUDIT = "AUDIT";
    private static final String MSG_STORE_NOT_FOUND = "找不到分店";

    private final InventoryItemJpaRepository itemRepository;
    private final InventoryBatchJpaRepository batchRepository;
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final InventoryTransactionJpaRepository transactionRepository;
    private final StoreJpaRepository storeRepository;
    private final UserJpaRepository UserJpaRepository;
    private final ControllerHelperService helperService;
    private final InventoryDeductionService deductionService;
    // [B-7] 使用營業時區的 Clock，盤盈批次的生產日 / 預設效期不再依賴 JVM 預設時區
    private final Clock clock;

    public InventoryAuditService(InventoryItemJpaRepository itemRepository,
                                 InventoryBatchJpaRepository batchRepository,
                                 InventorySnapshotJpaRepository snapshotRepository,
                                 InventoryTransactionJpaRepository transactionRepository,
                                 StoreJpaRepository storeRepository,
                                 UserJpaRepository UserJpaRepository,
                                 ControllerHelperService helperService,
                                 InventoryDeductionService deductionService,
                                 Clock clock) {
        this.clock = clock;
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.storeRepository = storeRepository;
        this.UserJpaRepository = UserJpaRepository;
        this.helperService = helperService;
        this.deductionService = deductionService;
    }

    /**
     * 執行盤點 (Audit)
     * 優化重點：
     * 1. 解決 N+1 問題：一次性獲取 Item, Snapshot 和 Batch。
     * 2. 解決 Transaction 失效問題：移除迴圈內的 self-invocation。
     * 3. 效能提升：盤損扣帳委派給 InventoryDeductionService 批次處理。
     */
    @Transactional
    public void performAudit(Long brandId, Long storeId, InventoryAuditRequestDto request) {
        // 1. 準備基礎資料
        StoreEntity StoreEntity = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_STORE_NOT_FOUND));
        UserEntity operator = UserJpaRepository.findByBrand_IdAndId(brandId, helperService.getCurrentUserId())
                .orElse(null);

        Set<Long> itemIds = request.getItems().stream()
                .map(InventoryAuditRequestDto.AuditItemDto::getInventoryItemId)
                .collect(Collectors.toSet());

        if (itemIds.isEmpty()) return;

        // 2. 批次查詢 Items 與 Snapshots
        // [B-5 修正] 改用 findByBrandIdAndIdInForUpdate：先依 id 順序鎖定品項，
        // 讓「讀取系統數量 → 計算差異 → 寫入」整段不會與訂單扣減 / 進貨交錯。
        Map<Long, InventoryItemEntity> itemMap = itemRepository.findByBrandIdAndIdInForUpdate(brandId, itemIds)
                .stream().collect(Collectors.toMap(InventoryItemEntity::getId, Function.identity()));

        if (itemMap.size() != itemIds.size()) {
            throw new ResourceNotFoundException("部分原物料 ID 無效或不屬於此品牌");
        }

        Map<Long, InventorySnapshotEntity> snapshotMap = snapshotRepository.findByStore_IdAndInventoryItem_IdIn(storeId, itemIds)
                .stream().collect(Collectors.toMap(s -> s.getInventoryItem().getId(), Function.identity()));

        // [B-5 修正] 系統數量 = 本店批次總和 (真實庫存)，不再使用可能與批次脫節的 Snapshot.quantity。
        // 沒有任何批次的品項不在結果中，視為 0。
        Map<Long, BigDecimal> systemQtyMap = batchRepository.sumQuantityByStoreAndItems(storeId, itemIds).stream()
                .collect(Collectors.toMap(
                        InventoryBatchJpaRepository.ItemQuantitySum::getItemId,
                        s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO));

        // 準備集合
        List<InventoryTransactionEntity> transactionsToSave = new ArrayList<>();
        List<InventorySnapshotEntity> snapshotsToSave = new ArrayList<>();
        Map<Long, BigDecimal> pendingDeductions = new HashMap<>(); // 收集需要扣庫存的項目 (ItemId -> Qty)

        // 3. 記憶體內計算差異 (包含盤盈/盤損判斷)
        calculateAuditDifferences(request, StoreEntity, operator, itemMap, snapshotMap, systemQtyMap,
                transactionsToSave, snapshotsToSave, pendingDeductions);

        // 4. 批次寫入 Transaction 與 Snapshot
        transactionRepository.saveAll(transactionsToSave);
        snapshotRepository.saveAll(snapshotsToSave);

        // 5. 盤損扣減 — 委派給 InventoryDeductionService 批次處理 FIFO
        if (!pendingDeductions.isEmpty()) {
            deductionService.processBatchDeductions(brandId, storeId, pendingDeductions);
        }
    }

    /**
     * 計算盤點差異：比較實際數量與系統數量，分流處理盤盈與盤損
     */
    private void calculateAuditDifferences(InventoryAuditRequestDto request, StoreEntity StoreEntity, UserEntity operator,
                                           Map<Long, InventoryItemEntity> itemMap, Map<Long, InventorySnapshotEntity> snapshotMap,
                                           Map<Long, BigDecimal> systemQtyMap,
                                           List<InventoryTransactionEntity> transactionsToSave, List<InventorySnapshotEntity> snapshotsToSave,
                                           Map<Long, BigDecimal> pendingDeductions) {
        for (InventoryAuditRequestDto.AuditItemDto itemDto : request.getItems()) {
            Long itemId = itemDto.getInventoryItemId();
            InventoryItemEntity item = itemMap.get(itemId);
            InventorySnapshotEntity snapshot = snapshotMap.get(itemId);

            // 處理新 Snapshot
            if (snapshot == null) {
                snapshot = new InventorySnapshotEntity();
                snapshot.setStore(StoreEntity);
                snapshot.setInventoryItem(item);
                snapshot.setQuantity(BigDecimal.ZERO);
                // [B-5] 放回 Map：同一品項若在請求中重複出現，才不會建立兩筆快照而違反唯一約束
                snapshotMap.put(itemId, snapshot);
            }

            // [B-5 修正] 系統數量 = 本店批次總和 (不是 Snapshot.quantity)
            BigDecimal currentQty = systemQtyMap.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal actualQty = itemDto.getActualQuantity();
            BigDecimal diff = actualQty.subtract(currentQty);

            // 只有當數量有變化時才產生 Transaction
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                InventoryTransactionEntity trx = new InventoryTransactionEntity();
                trx.setStore(StoreEntity);
                trx.setInventoryItem(item);
                trx.setChangeAmount(diff);
                trx.setBalanceAfter(actualQty);
                trx.setReasonType(REASON_TYPE_AUDIT);
                trx.setOperator(operator);
                trx.setNote(buildAuditNote(request.getNote(), itemDto.getItemNote()));
                transactionsToSave.add(trx);
            }

            // [B-5 修正] 無論有無差異，盤點後 Snapshot 一律對齊實盤數量並更新盤點時間
            // (舊版 Snapshot 與批次脫節時，即使 diff 為 0 也能藉由盤點修正回來)
            snapshot.setQuantity(actualQty);
            snapshot.setLastCheckedAt(Instant.now());
            if (!snapshotsToSave.contains(snapshot)) {
                snapshotsToSave.add(snapshot);
            }

            // 分流處理：盤盈 vs 盤損
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // 盤盈 (Gain): 立即建立新批次
                handleInventoryGain(StoreEntity, item, diff, itemDto.getGainedItemExpiryDate());
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                // 盤損 (Loss): 收集起來，稍後批次扣減 (重複品項時累加)
                pendingDeductions.merge(itemId, diff.abs(), BigDecimal::add);
            }

            // [B-5] 更新記憶體內的系統數量，讓同一品項重複出現時，第二筆以第一筆處理後的數量為基準
            systemQtyMap.put(itemId, actualQty);
        }
    }

    /**
     * 取得盤點清單 (Audit List)
     * 列出品牌下所有原物料，並關聯該分店目前的庫存快照。
     */
    @Transactional(readOnly = true)
    public List<InventoryAuditItemResponseDto> getAuditList(Long brandId, Long storeId) {
        // 1. 查詢該品牌所有定義的原物料 (Master Data)
        List<InventoryItemEntity> allItems = itemRepository.findByBrand_Id(brandId);

        // 2. 查詢該分店目前各品項的庫存
        // [B-5 修正] 與 performAudit 使用同一來源 (本店批次總和)，
        // 避免畫面顯示的「系統庫存」與送出盤點時用來計算差異的數量不同
        Map<Long, BigDecimal> stockMap = batchRepository.sumQuantityByStore(storeId).stream()
                .collect(Collectors.toMap(
                        InventoryBatchJpaRepository.ItemQuantitySum::getItemId,
                        s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO
                ));

        // 3. 組合結果
        return allItems.stream()
                .map(item -> new InventoryAuditItemResponseDto(
                        item.getId(),
                        item.getName(),
                        item.getUnit(),
                        stockMap.getOrDefault(item.getId(), BigDecimal.ZERO)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 合併主單備註與單項備註
     * 格式範例: "月底盤點 | 破損報廢"
     */
    private String buildAuditNote(String mainNote, String itemNote) {
        StringBuilder noteBuilder = new StringBuilder();

        if (mainNote != null && !mainNote.isEmpty()) {
            noteBuilder.append(mainNote);
        }

        if (itemNote != null && !itemNote.isEmpty()) {
            if (!noteBuilder.isEmpty()) {
                noteBuilder.append(" | ");
            }
            noteBuilder.append(itemNote);
        }

        return noteBuilder.toString();
    }

    /**
     * 處理盤盈：建立新批次並更新 InventoryItemEntity 的 TotalQuantity
     */
    private void handleInventoryGain(StoreEntity StoreEntity, InventoryItemEntity item, BigDecimal quantityToGain, LocalDate manualExpiryDate) {
        // 1. 建立新批次
        InventoryBatchEntity newBatch = new InventoryBatchEntity();
        newBatch.setStore(StoreEntity);
        newBatch.setInventoryItem(item);
        newBatch.setQuantityReceived(quantityToGain);
        newBatch.setCurrentQuantity(quantityToGain);
        newBatch.setShipment(null);
        LocalDate today = LocalDate.now(clock);
        newBatch.setProductionDate(today);

        if (manualExpiryDate != null) {
            newBatch.setExpiryDate(manualExpiryDate);
        } else {
            // 若未指定到期日，取最近一筆批次的到期日作為預設值
            LocalDate estimatedExpiry = batchRepository
                    .findTopByStore_IdAndInventoryItem_IdOrderByExpiryDateDesc(StoreEntity.getId(), item.getId())
                    .map(InventoryBatchEntity::getExpiryDate)
                    .orElse(today.plusDays(7)); // TODO: 建議改讀取 Item 的預設效期設定
            newBatch.setExpiryDate(estimatedExpiry);
        }
        batchRepository.save(newBatch);

        // 2. 更新 InventoryItemEntity 總量以保持一致性
        itemRepository.increaseTotalQuantity(item.getId(), quantityToGain);
    }
}
