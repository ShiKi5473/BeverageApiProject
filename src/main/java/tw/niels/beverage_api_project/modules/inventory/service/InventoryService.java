package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.*;
import tw.niels.beverage_api_project.modules.inventory.repository.*;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 庫存服務 — 負責進貨 (Shipment) 與庫存查詢
 * FIFO 扣庫存邏輯已拆分至 {@link InventoryDeductionService}
 * 盤點邏輯已拆分至 {@link InventoryAuditService}
 */
@Service
public class InventoryService {

    private static final String REASON_TYPE_RESTOCK = "RESTOCK";
    private static final String SHIPMENT_NOTE_PREFIX = "進貨單號: ";
    private static final String MSG_STORE_NOT_FOUND = "找不到分店";

    private final InventoryItemJpaRepository itemRepository;
    private final InventoryBatchJpaRepository batchRepository;
    private final PurchaseShipmentJpaRepository shipmentRepository;
    private final StoreJpaRepository storeRepository;
    private final UserJpaRepository UserJpaRepository;
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final InventoryTransactionJpaRepository transactionRepository;
    // [B-7] 使用營業時區的 Clock (TimeConfig)，不再依賴 JVM 預設時區
    private final Clock clock;

    public InventoryService(InventoryItemJpaRepository itemRepository,
                            InventoryBatchJpaRepository batchRepository,
                            PurchaseShipmentJpaRepository shipmentRepository,
                            StoreJpaRepository storeRepository,
                            UserJpaRepository UserJpaRepository,
                            InventorySnapshotJpaRepository snapshotRepository,
                            InventoryTransactionJpaRepository transactionRepository,
                            Clock clock) {
        this.clock = clock;
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.shipmentRepository = shipmentRepository;
        this.storeRepository = storeRepository;
        this.UserJpaRepository = UserJpaRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 員工執行進貨操作 (Add Stock)
     */
    @Transactional
    public PurchaseShipmentEntity addShipment(Long brandId, Long storeId, Long userId, AddShipmentRequestDto request) {
        StoreEntity StoreEntity = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_STORE_NOT_FOUND));

        UserEntity staff = UserJpaRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到員工，ID：" + userId));

        // 1. 建立進貨單主檔
        PurchaseShipmentEntity shipment = new PurchaseShipmentEntity();
        shipment.setStore(StoreEntity);
        shipment.setStaff(staff);
        shipment.setShipmentDate(LocalDateTime.now(clock));
        shipment.setSupplier(request.getSupplier());
        shipment.setInvoiceNo(request.getInvoiceNo());
        shipment.setNotes(request.getNotes());
        shipment = shipmentRepository.save(shipment);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return shipment;
        }

        // 2. [優化] 收集所有 Item ID
        Set<Long> itemIds = request.getItems().stream()
                .map(AddShipmentRequestDto.BatchItemDto::getInventoryItemId)
                .collect(Collectors.toSet());

        // 3. [優化] 批次查詢 InventoryItemEntity (Map<ItemId, Item>)
        Map<Long, InventoryItemEntity> itemMap = itemRepository.findByBrand_IdAndIdIn(brandId, itemIds)
                .stream()
                .collect(Collectors.toMap(InventoryItemEntity::getId, Function.identity()));

        if (itemMap.size() != itemIds.size()) {
            throw new ResourceNotFoundException("部分原物料 ID 不存在或不屬於該品牌");
        }

        // 4. [優化] 批次查詢 InventorySnapshotEntity (Map<ItemId, Snapshot>)
        // 這一步直接查出所有現有的 Snapshot，避免迴圈內 Select
        Map<Long, InventorySnapshotEntity> snapshotMap = snapshotRepository.findByStore_IdAndInventoryItem_IdIn(storeId, itemIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getInventoryItem().getId(), Function.identity()));

        // 準備批次儲存的 List
        List<InventoryBatchEntity> batchesToSave = new ArrayList<>();
        List<InventoryTransactionEntity> transactionsToSave = new ArrayList<>();
        // [B-5 新增] 依品項彙總本次進貨量 (同一品項可能在請求中出現多次)，用 TreeMap 讓更新順序固定以降低死鎖機率
        Map<Long, BigDecimal> receivedByItem = new TreeMap<>();

        // 5. 記憶體內處理邏輯
        for (AddShipmentRequestDto.BatchItemDto itemDto : request.getItems()) {
            InventoryItemEntity item = itemMap.get(itemDto.getInventoryItemId());

            // 建立 Batch
            InventoryBatchEntity batch = new InventoryBatchEntity();
            batch.setStore(StoreEntity); // [修正] 記得設定 StoreEntity
            batch.setShipment(shipment);
            batch.setInventoryItem(item);
            batch.setQuantityReceived(itemDto.getQuantity());
            batch.setCurrentQuantity(itemDto.getQuantity());
            batch.setExpiryDate(itemDto.getExpiryDate());
            batchesToSave.add(batch);

            // [B-5 新增] 累計此品項的進貨量，迴圈結束後一次累加到品牌層級 totalQuantity
            receivedByItem.merge(item.getId(), itemDto.getQuantity(), BigDecimal::add);

            // 更新或建立 Snapshot (從 Map 取得，如果沒有則建立新的並放入 Map)
            InventorySnapshotEntity snapshot = snapshotMap.computeIfAbsent(item.getId(), k -> {
                InventorySnapshotEntity newSnap = new InventorySnapshotEntity();
                newSnap.setStore(StoreEntity);
                newSnap.setInventoryItem(item);
                newSnap.setQuantity(BigDecimal.ZERO);
                return newSnap;
            });

            // 累加數量
            BigDecimal newQuantity = snapshot.getQuantity().add(itemDto.getQuantity());
            snapshot.setQuantity(newQuantity);
            // snapshot.setLastCheckedAt(Instant.now()); // 若業務需要可更新時間

            // 建立 Transaction
            InventoryTransactionEntity trx = new InventoryTransactionEntity();
            trx.setStore(StoreEntity);
            trx.setInventoryItem(item);
            trx.setChangeAmount(itemDto.getQuantity());
            trx.setBalanceAfter(newQuantity);
            trx.setReasonType(REASON_TYPE_RESTOCK);
            trx.setOperator(staff);
            trx.setNote(SHIPMENT_NOTE_PREFIX + shipment.getId());
            transactionsToSave.add(trx);
        }

        // 6. [優化] 批次寫入資料庫
        batchRepository.saveAll(batchesToSave);
        snapshotRepository.saveAll(snapshotMap.values()); // 寫入所有更新後的 Snapshot
        transactionRepository.saveAll(transactionsToSave);

        // 7. [B-5 修正] 進貨同步增加品牌層級 InventoryItem.totalQuantity
        // 原本只有盤盈會加 totalQuantity、進貨不加，導致三套庫存數字逐漸分歧。
        // 使用原子的 UPDATE ... SET total = total + :amount，不必先鎖定品項即可安全累加。
        receivedByItem.forEach(itemRepository::increaseTotalQuantity);

        return shipment;
    }

    /**
     * 查詢即時庫存
     * [B-5 修正] 以本店批次總和為準 (與扣減 / 盤點使用同一來源)；
     * 沒有批次時才退回讀取 Snapshot，兩者皆無則回傳 0
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
        return batchRepository.sumQuantityByStoreAndItem(storeId, itemId)
                .orElseGet(() -> snapshotRepository.findByStore_IdAndInventoryItem_Id(storeId, itemId)
                        .map(InventorySnapshotEntity::getQuantity)
                        .orElse(BigDecimal.ZERO));
    }
}
