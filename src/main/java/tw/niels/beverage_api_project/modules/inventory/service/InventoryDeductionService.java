package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ApplicationException;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshotEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 庫存扣減服務 — 負責 FIFO (先進先出) 庫存扣減邏輯
 *
 * <h3>FIFO 演算法說明：</h3>
 * <p>扣庫存時依照批次的到期日 (expiryDate) 由近到遠排序，優先扣減即將過期的批次。</p>
 * <ol>
 *   <li>先鎖定 InventoryItemEntity (悲觀鎖)，再於鎖內以「分店批次總和」檢查庫存是否足夠 (Fast Fail)。</li>
 *   <li>查詢該品項在該分店的所有可用批次 (同樣加悲觀鎖)，依到期日升序排列。</li>
 *   <li>從最早到期的批次開始扣減：若該批次數量 >= 剩餘待扣量，直接扣除；否則扣光該批次，繼續扣下一批。</li>
 *   <li>使用 JDBC Batch Update 一次性寫入所有批次的新數量，減少資料庫交互。</li>
 *   <li>同步扣減品牌層級 totalQuantity，並把分店 InventorySnapshot 對齊為扣減後的批次總和。</li>
 * </ol>
 *
 * <h3>三種庫存數字的關係 (B-5)：</h3>
 * <ul>
 *   <li>分店批次總和 (inventory_batches)：真實庫存來源 (source of truth)，扣減與盤點都以它為準。</li>
 *   <li>分店 InventorySnapshot：批次總和的快照，每次進貨 / 扣減 / 盤點後都要同步，否則盤點會誤判盤損。</li>
 *   <li>品牌 InventoryItem.totalQuantity：各分店批次總和的加總 (僅作彙總參考)。</li>
 * </ul>
 *
 * <h3>混合架構：</h3>
 * <ul>
 *   <li>讀取 (Read)：使用 JPA Repository 查詢可用批次</li>
 *   <li>寫入 (Write)：使用 JDBC DAO 進行批次更新，提升效能</li>
 * </ul>
 */
@Service
public class InventoryDeductionService {

    private static final String MSG_INSUFFICIENT_STOCK = "error.inventory.insufficient";

    private final InventoryItemJpaRepository itemRepository;
    private final InventoryBatchJpaRepository batchRepository;
    private final InventoryBatchDAO inventoryBatchDAO;
    private final StoreJpaRepository storeRepository;
    // [B-5 新增] 扣減後需要同步分店庫存快照
    private final InventorySnapshotJpaRepository snapshotRepository;

    public InventoryDeductionService(InventoryItemJpaRepository itemRepository,
                                     InventoryBatchJpaRepository batchRepository,
                                     InventoryBatchDAO inventoryBatchDAO,
                                     StoreJpaRepository storeRepository,
                                     InventorySnapshotJpaRepository snapshotRepository) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.inventoryBatchDAO = inventoryBatchDAO;
        this.storeRepository = storeRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * 核心 FIFO 庫存扣減邏輯 (單品項)
     * 流程：鎖定品項 → 以分店批次總和檢查 → 扣品牌總量 → FIFO 批次扣減 → JDBC 批次寫入 → 同步快照
     */
    @Transactional
    public void deductInventory(Long brandId, Long storeId, Long itemId, BigDecimal quantityToDeduct) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("扣減數量必須大於 0");
        }

        // 1. 鎖定原物料 (Item Level Lock)
        InventoryItemEntity item = itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Item ID: " + itemId));

        // 2. 檢查庫存 (Fast Fail)
        // [B-5 修正] 原本用「品牌層級」totalQuantity 檢查，但實際扣的是「分店層級」批次，
        // 導致別家分店有庫存時誤放行、或進貨未累加 total 時誤判不足。
        // 現在改用本店批次總和檢查，且在品項鎖內執行，避免與其他扣減交易交錯。
        BigDecimal storeQuantity = batchRepository.sumQuantityByStoreAndItem(storeId, itemId)
                .orElse(BigDecimal.ZERO);
        if (storeQuantity.compareTo(quantityToDeduct) < 0) {
            throw new BadRequestException(
                    MSG_INSUFFICIENT_STOCK,
                    item.getName(),
                    storeQuantity,
                    quantityToDeduct
            );
        }

        // 3. 扣減品牌層級總庫存 (Item Table) - 單筆更新保留 JPA
        // [B-5] 舊資料可能因進貨曾未累加 total 而偏小，下限取 0 以免觸發 chk_inventory_total_quantity (>= 0)
        item.setTotalQuantity(item.getTotalQuantity().subtract(quantityToDeduct).max(BigDecimal.ZERO));
        itemRepository.save(item);

        // 4. FIFO 批次扣減計算（依到期日升序，優先扣減即將過期的批次）
        List<InventoryBatchEntity> batches = batchRepository.findAvailableBatchesForUpdate(storeId, itemId);

        List<InventoryBatchDAO.BatchUpdateTuple> batchUpdates = new ArrayList<>();
        BigDecimal remainingToDeduct = quantityToDeduct;

        for (InventoryBatchEntity batch : batches) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal currentQty = batch.getCurrentQuantity();
            BigDecimal newQty;

            if (currentQty.compareTo(remainingToDeduct) >= 0) {
                // 此批次足夠扣減全部剩餘量
                newQty = currentQty.subtract(remainingToDeduct);
                remainingToDeduct = BigDecimal.ZERO;
            } else {
                // 此批次不足，扣光後繼續下一批
                newQty = BigDecimal.ZERO;
                remainingToDeduct = remainingToDeduct.subtract(currentQty);
            }

            batchUpdates.add(new InventoryBatchDAO.BatchUpdateTuple(batch.getId(), newQty));
        }

        // 5. 呼叫 DAO 執行 JDBC 批次更新
        if (!batchUpdates.isEmpty()) {
            inventoryBatchDAO.batchUpdateQuantities(batchUpdates);
        }

        // 6. 二次檢查：理論上不應發生，但確保資料一致性
        if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
            throw new ApplicationException("庫存資料異常：總量檢查通過但批次不足。短缺: " + remainingToDeduct);
        }

        // 7. [B-5 新增] 同步分店庫存快照 = 扣減後的批次總和
        // 否則下次盤點時，快照仍是舊值，已賣出的數量會被當成「盤損」再扣一次批次。
        Map<Long, BigDecimal> remainingByItem = new HashMap<>();
        remainingByItem.put(itemId, storeQuantity.subtract(quantityToDeduct));
        syncSnapshots(storeId, remainingByItem);
    }

    /**
     * 簡化版扣減 — 透過 storeId 反查 brandId 後委派給完整版
     */
    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantity) {
        // 透過 storeId 查詢 Store 取得 brandId
        var store = storeRepository.findByIdSystem(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Store ID: " + storeId));
        deductInventory(store.getBrand().getId(), storeId, itemId, quantity);
    }

    /**
     * 批次處理多品項庫存扣減 (供訂單完成 / 盤點盤損使用)
     * 與單品項扣減不同，此方法一次鎖定多個 Item 並批次計算 FIFO，減少 DB 交互次數。
     *
     * @param brandId      品牌 ID
     * @param storeId      分店 ID
     * @param deductionMap 待扣減的品項與數量 (Map&lt;ItemId, Quantity&gt;)
     */
    @Transactional
    public void processBatchDeductions(Long brandId, Long storeId, Map<Long, BigDecimal> deductionMap) {
        // [B-5] 一律依 itemId 排序處理，與 findByBrandIdAndIdInForUpdate 的 ORDER BY 一致，避免死鎖
        SortedMap<Long, BigDecimal> sortedDeductions = new TreeMap<>(deductionMap);
        Set<Long> itemIds = sortedDeductions.keySet();

        // A. 批次鎖定 InventoryItemEntity (防止並發修改總量)
        List<InventoryItemEntity> itemsToUpdate = itemRepository.findByBrandIdAndIdInForUpdate(brandId, itemIds);
        Map<Long, InventoryItemEntity> lockedItemMap = itemsToUpdate.stream()
                .collect(Collectors.toMap(InventoryItemEntity::getId, Function.identity()));

        // B. [B-5 修正] 在品項鎖內，批次查詢並「鎖定」本店所有相關的可用批次
        // 原本使用未加鎖的 findAvailableBatchesForItems，現改為 PESSIMISTIC_WRITE 版本
        List<InventoryBatchEntity> allBatches = batchRepository.findAvailableBatchesForItemsForUpdate(storeId, itemIds);

        // 將 Batches 依照 Item 分組 (保留 repository 的到期日排序)
        Map<Long, List<InventoryBatchEntity>> batchesByItem = allBatches.stream()
                .collect(Collectors.groupingBy(b -> b.getInventoryItem().getId()));

        // 準備 JDBC Batch Update 列表，以及扣減後各品項的本店批次總和 (用來同步快照)
        List<InventoryBatchDAO.BatchUpdateTuple> batchUpdates = new ArrayList<>();
        Map<Long, BigDecimal> remainingByItem = new HashMap<>();

        // C. 檢查庫存 + 更新 Item 總量 + 記憶體內計算 FIFO 扣減（每個品項各自依到期日順序扣減）
        for (Map.Entry<Long, BigDecimal> entry : sortedDeductions.entrySet()) {
            Long itemId = entry.getKey();
            BigDecimal qtyToDeduct = entry.getValue();
            InventoryItemEntity item = lockedItemMap.get(itemId);

            if (item == null) {
                throw new ResourceNotFoundException("扣減庫存時找不到品項，ID：" + itemId);
            }

            List<InventoryBatchEntity> itemBatches = batchesByItem.getOrDefault(itemId, Collections.emptyList());

            // [B-5 修正] Fast Fail 改用「本店批次總和」(已被鎖定的批次在記憶體內加總)，不再用品牌層級 totalQuantity
            BigDecimal storeQuantity = itemBatches.stream()
                    .map(InventoryBatchEntity::getCurrentQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (storeQuantity.compareTo(qtyToDeduct) < 0) {
                throw new BadRequestException(
                        MSG_INSUFFICIENT_STOCK,
                        item.getName(),
                        storeQuantity,
                        qtyToDeduct
                );
            }

            // 仍需同步扣減品牌層級 totalQuantity (下限 0，避免舊資料偏小時違反 CHECK 約束)
            item.setTotalQuantity(item.getTotalQuantity().subtract(qtyToDeduct).max(BigDecimal.ZERO));

            BigDecimal remainingToDeduct = qtyToDeduct;
            for (InventoryBatchEntity batch : itemBatches) {
                if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal currentQty = batch.getCurrentQuantity();
                BigDecimal newQty;

                if (currentQty.compareTo(remainingToDeduct) >= 0) {
                    newQty = currentQty.subtract(remainingToDeduct);
                    remainingToDeduct = BigDecimal.ZERO;
                } else {
                    newQty = BigDecimal.ZERO;
                    remainingToDeduct = remainingToDeduct.subtract(currentQty);
                }

                batchUpdates.add(new InventoryBatchDAO.BatchUpdateTuple(batch.getId(), newQty));
            }

            // 二次檢查：確保批次總和足夠
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
                throw new ApplicationException("庫存資料不一致：Item總量檢查通過，但實際批次總和不足。Item ID: " + itemId + ", 短缺: " + remainingToDeduct);
            }

            remainingByItem.put(itemId, storeQuantity.subtract(qtyToDeduct));
        }
        itemRepository.saveAll(itemsToUpdate);

        // D. 執行 JDBC 批次更新
        if (!batchUpdates.isEmpty()) {
            inventoryBatchDAO.batchUpdateQuantities(batchUpdates);
        }

        // E. [B-5 新增] 同步分店庫存快照 = 扣減後的批次總和
        syncSnapshots(storeId, remainingByItem);
    }

    /**
     * [B-5 新增] 把分店庫存快照 (InventorySnapshot) 對齊為指定的數量 (扣減後的批次總和)。
     * 快照不存在時會建立新的一筆；一次批次查詢 + 一次 saveAll，避免 N+1。
     *
     * @param storeId         分店 ID
     * @param quantityByItem  Map&lt;ItemId, 扣減後的本店庫存量&gt;
     */
    private void syncSnapshots(Long storeId, Map<Long, BigDecimal> quantityByItem) {
        if (quantityByItem.isEmpty()) {
            return;
        }

        Map<Long, InventorySnapshotEntity> snapshotMap = new HashMap<>();
        for (InventorySnapshotEntity s : snapshotRepository.findByStore_IdAndInventoryItem_IdIn(storeId, quantityByItem.keySet())) {
            snapshotMap.put(s.getInventoryItem().getId(), s);
        }

        List<InventorySnapshotEntity> toSave = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : quantityByItem.entrySet()) {
            InventorySnapshotEntity snapshot = snapshotMap.get(entry.getKey());
            if (snapshot == null) {
                // 沒有快照 (例如只靠批次建立的庫存) → 建立一筆，之後盤點才有一致的基準
                snapshot = new InventorySnapshotEntity();
                snapshot.setStore(storeRepository.getReferenceById(storeId));
                snapshot.setInventoryItem(itemRepository.getReferenceById(entry.getKey()));
            }
            // 數量不可為負
            snapshot.setQuantity(entry.getValue().max(BigDecimal.ZERO));
            toSave.add(snapshot);
        }
        snapshotRepository.saveAll(toSave);
    }
}
