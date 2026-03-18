package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.*;
import tw.niels.beverage_api_project.modules.inventory.repository.*;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;
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
    private static final String MSG_STORE_NOT_FOUND = "Store not found";

    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final PurchaseShipmentRepository shipmentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ControllerHelperService helperService;
    private final InventorySnapshotRepository snapshotRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(InventoryItemRepository itemRepository,
                            InventoryBatchRepository batchRepository,
                            PurchaseShipmentRepository shipmentRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            ControllerHelperService helperService,
                            InventorySnapshotRepository snapshotRepository,
                            InventoryTransactionRepository transactionRepository) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.shipmentRepository = shipmentRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.helperService = helperService;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 員工執行進貨操作 (Add Stock)
     */
    @Transactional
    public PurchaseShipment addShipment(Long brandId, Long storeId, AddShipmentRequestDto request) {
        Store store = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_STORE_NOT_FOUND));

        Long userId = helperService.getCurrentUserId();
        User staff = userRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + userId));

        // 1. 建立進貨單主檔
        PurchaseShipment shipment = new PurchaseShipment();
        shipment.setStore(store);
        shipment.setStaff(staff);
        shipment.setShipmentDate(LocalDateTime.now());
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

        // 3. [優化] 批次查詢 InventoryItem (Map<ItemId, Item>)
        Map<Long, InventoryItem> itemMap = itemRepository.findByBrand_IdAndIdIn(brandId, itemIds)
                .stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        if (itemMap.size() != itemIds.size()) {
            throw new ResourceNotFoundException("部分原物料 ID 不存在或不屬於該品牌");
        }

        // 4. [優化] 批次查詢 InventorySnapshot (Map<ItemId, Snapshot>)
        // 這一步直接查出所有現有的 Snapshot，避免迴圈內 Select
        Map<Long, InventorySnapshot> snapshotMap = snapshotRepository.findByStore_IdAndInventoryItem_IdIn(storeId, itemIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getInventoryItem().getId(), Function.identity()));

        // 準備批次儲存的 List
        List<InventoryBatch> batchesToSave = new ArrayList<>();
        List<InventoryTransaction> transactionsToSave = new ArrayList<>();

        // 5. 記憶體內處理邏輯
        for (AddShipmentRequestDto.BatchItemDto itemDto : request.getItems()) {
            InventoryItem item = itemMap.get(itemDto.getInventoryItemId());

            // 建立 Batch
            InventoryBatch batch = new InventoryBatch();
            batch.setStore(store); // [修正] 記得設定 Store
            batch.setShipment(shipment);
            batch.setInventoryItem(item);
            batch.setQuantityReceived(itemDto.getQuantity());
            batch.setCurrentQuantity(itemDto.getQuantity());
            batch.setExpiryDate(itemDto.getExpiryDate());
            batchesToSave.add(batch);

            // 更新或建立 Snapshot (從 Map 取得，如果沒有則建立新的並放入 Map)
            InventorySnapshot snapshot = snapshotMap.computeIfAbsent(item.getId(), k -> {
                InventorySnapshot newSnap = new InventorySnapshot();
                newSnap.setStore(store);
                newSnap.setInventoryItem(item);
                newSnap.setQuantity(BigDecimal.ZERO);
                return newSnap;
            });

            // 累加數量
            BigDecimal newQuantity = snapshot.getQuantity().add(itemDto.getQuantity());
            snapshot.setQuantity(newQuantity);
            // snapshot.setLastCheckedAt(Instant.now()); // 若業務需要可更新時間

            // 建立 Transaction
            InventoryTransaction trx = new InventoryTransaction();
            trx.setStore(store);
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

        return shipment;
    }

    /**
     * 查詢即時庫存
     * 優先從 Snapshot 讀取，若無則回傳 0
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
        return snapshotRepository.findByStore_IdAndInventoryItem_Id(storeId, itemId)
                .map(InventorySnapshot::getQuantity)
                .orElse(BigDecimal.ZERO);
    }
}
