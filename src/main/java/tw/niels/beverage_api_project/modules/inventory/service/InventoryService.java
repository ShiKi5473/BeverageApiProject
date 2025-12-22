package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.*;
import tw.niels.beverage_api_project.modules.inventory.repository.*;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final PurchaseShipmentRepository shipmentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ControllerHelperService helperService;
    private final InventoryBatchDAO inventoryBatchDAO;
    private final InventorySnapshotRepository snapshotRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(InventoryItemRepository itemRepository,
                            InventoryBatchRepository batchRepository,
                            InventoryBatchDAO inventoryBatchDAO,
                            PurchaseShipmentRepository shipmentRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            ControllerHelperService helperService,
                            InventorySnapshotRepository snapshotRepository,
                            InventoryTransactionRepository transactionRepository) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.inventoryBatchDAO = inventoryBatchDAO;
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
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));

        Long userId = helperService.getCurrentUserId();
        User staff = userRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + userId));

        // 1. 建立進貨單主檔
        PurchaseShipment shipment = new PurchaseShipment();
        shipment.setStore(store);
        shipment.setStaff(staff);
        shipment.setShipmentDate(LocalDateTime.now());
        shipment.setSupplier(request.getSupplier());
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
            trx.setReasonType("RESTOCK");
            trx.setOperator(staff);
            trx.setNote("進貨單號: " + shipment.getId());
            transactionsToSave.add(trx);
        }

        // 6. [優化] 批次寫入資料庫
        batchRepository.saveAll(batchesToSave);
        snapshotRepository.saveAll(snapshotMap.values()); // 寫入所有更新後的 Snapshot
        transactionRepository.saveAll(transactionsToSave);

        return shipment;

    }

    /**
     * 核心 FIFO 庫存扣減邏輯 (Deduct Inventory)
     * 【混合架構優化】：
     * 1. 使用 JPA Repository 查詢可用批次 (Read)。
     * 2. 使用 JDBC DAO 進行批次更新 (Write)，提升效能。
     */
    @Transactional
    public void deductInventory(Long brandId, Long storeId, Long itemId, BigDecimal quantityToDeduct) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("扣減數量必須大於 0");
        }

        // 1. 鎖定原物料 (Item Level Lock)
        InventoryItem item = itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Item ID: " + itemId));

        // 2. 檢查總庫存 (Fast Fail)
        if (item.getTotalQuantity().compareTo(quantityToDeduct) < 0) {
            throw new BadRequestException(
                    "error.inventory.insufficient",
                    item.getName(),
                    item.getTotalQuantity(),
                    quantityToDeduct
            );
        }

        // 3. 預扣總庫存 (Item Table) - 單筆更新保留 JPA
        item.setTotalQuantity(item.getTotalQuantity().subtract(quantityToDeduct));
        itemRepository.save(item);

        // 4. FIFO 批次扣減計算
        List<InventoryBatch> batches = batchRepository.findAvailableBatchesForUpdate(storeId, itemId);

        // 準備批次更新列表 (DTO List)
        List<InventoryBatchDAO.BatchUpdateTuple> batchUpdates = new ArrayList<>();
        BigDecimal remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal currentQty = batch.getCurrentQuantity();
            BigDecimal newQty;

            if (currentQty.compareTo(remainingToDeduct) >= 0) {
                // 此批次足夠
                newQty = currentQty.subtract(remainingToDeduct);
                remainingToDeduct = BigDecimal.ZERO;
            } else {
                // 此批次不足，扣光
                newQty = BigDecimal.ZERO;
                remainingToDeduct = remainingToDeduct.subtract(currentQty);
            }

            // 將 ID 與 新數量 加入列表
            batchUpdates.add(new tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO.BatchUpdateTuple(batch.getId(), newQty));
        }

        // 5. 呼叫 DAO 執行 JDBC 批次更新
        if (!batchUpdates.isEmpty()) {
            inventoryBatchDAO.batchUpdateQuantities(batchUpdates);
        }

        // 6. 二次檢查
        if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("庫存資料異常：總量檢查通過但批次不足。短缺: " + remainingToDeduct);
        }
    }

    @Transactional
    public void performAudit(Long brandId, Long storeId, InventoryAuditRequestDto request) {
        // 1. 準備資料 (一次性查詢 Store & Operator)
        Store store = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        User operator = userRepository.findByBrand_IdAndId(brandId, helperService.getCurrentUserId())
                .orElse(null);

        // 2. 收集所有 Item IDs
        Set<Long> itemIds = request.getItems().stream()
                .map(InventoryAuditRequestDto.AuditItemDto::getInventoryItemId)
                .collect(Collectors.toSet());

        // 3. 【優化】批次查詢 Items (1 次 SQL)
        List<InventoryItem> items = itemRepository.findByBrand_IdAndIdIn(brandId, itemIds);
        // 轉為 Map 方便後續查找: Map<ItemId, InventoryItem>
        Map<Long, InventoryItem> itemMap = items.stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        // 檢查是否有無效 ID
        if (items.size() != itemIds.size()) {
            throw new ResourceNotFoundException("部分原物料 ID 無效或不屬於此品牌");
        }

        // 4. 【優化】批次查詢 Snapshots (1 次 SQL)
        List<InventorySnapshot> snapshots = snapshotRepository.findByStore_IdAndInventoryItem_IdIn(storeId, itemIds);
        // 轉為 Map: Map<ItemId, InventorySnapshot>
        Map<Long, InventorySnapshot> snapshotMap = snapshots.stream()
                .collect(Collectors.toMap(s -> s.getInventoryItem().getId(), Function.identity()));

        // 準備批次儲存的 List
        List<InventoryTransaction> transactionsToSave = new ArrayList<>();
        List<InventorySnapshot> snapshotsToSave = new ArrayList<>();

        // 5. 在記憶體中進行邏輯處理 (純 Java 運算，極快)
        for (InventoryAuditRequestDto.AuditItemDto itemDto : request.getItems()) {
            Long itemId = itemDto.getInventoryItemId();
            InventoryItem item = itemMap.get(itemId); // 從 Map 取，不查 DB

            InventorySnapshot snapshot = snapshotMap.getOrDefault(itemId, new InventorySnapshot());

            // 改為判斷關聯是否為空，或是利用 isNew() (如果 BaseTsidEntity 有實作)
            if (snapshot.getStore() == null) {
                snapshot.setStore(store);
                snapshot.setInventoryItem(item);
                snapshot.setQuantity(BigDecimal.ZERO);
            }

            BigDecimal currentQty = snapshot.getQuantity();
            BigDecimal actualQty = itemDto.getActualQuantity();
            BigDecimal diff = actualQty.subtract(currentQty);

            // 建立 Transaction 物件
            InventoryTransaction trx = new InventoryTransaction();
            trx.setStore(store);
            trx.setInventoryItem(item);
            trx.setChangeAmount(diff);
            trx.setBalanceAfter(actualQty);
            trx.setReasonType("AUDIT");
            trx.setOperator(operator);

            // 目標：將「主單備註」與「單項備註」合併，例如："月底盤點 | 破損報廢"
            String combinedNote = buildAuditNote(request.getNote(), itemDto.getItemNote());

            // 3. 設定回 Transaction 實體
            trx.setNote(combinedNote);

            transactionsToSave.add(trx);

            // 更新 Snapshot 物件
            snapshot.setQuantity(actualQty);
            snapshot.setLastCheckedAt(Instant.now());
            snapshotsToSave.add(snapshot);
        }

        // 6. 【優化】批次寫入 (使用 saveAll)
        transactionRepository.saveAll(transactionsToSave);
        snapshotRepository.saveAll(snapshotsToSave);
    }



    /**
     * 查詢即時庫存 (改寫 getCurrentStock)
     * Phase 3 修改：優先從 Snapshot 讀取，若無則回傳 0
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
        // 從新的 Snapshot 表讀取
        return snapshotRepository.findByStore_IdAndInventoryItem_Id(storeId, itemId)
                .map(InventorySnapshot::getQuantity)
                .orElse(BigDecimal.ZERO);
    }


    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantity) {
        Store store = storeRepository.findByIdSystem(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Store ID: " + storeId));
        deductInventory(store.getBrand().getId(), storeId, itemId, quantity);
    }

    /**
     * 取得盤點清單 (Audit List)
     * 邏輯：列出品牌下所有原物料，並關聯該分店目前的庫存快照。
     */
    @Transactional(readOnly = true)
    public List<InventoryAuditItemResponseDto> getAuditList(Long brandId, Long storeId) {
        // 1. 查詢該品牌所有定義的原物料 (Master Data)
        List<InventoryItem> allItems = itemRepository.findByBrand_Id(brandId); // 需確認 Repository 有此方法

        // 2. 查詢該分店目前的庫存快照 (Transaction Data)
        List<InventorySnapshot> snapshots = snapshotRepository.findByStore_Id(storeId);

        // 轉為 Map<ItemId, Quantity> 以便快速查找
        Map<Long, BigDecimal> stockMap = snapshots.stream()
                .collect(Collectors.toMap(
                        s -> s.getInventoryItem().getId(),
                        InventorySnapshot::getQuantity
                ));

        // 3. 組合結果 (使用 Record 建構子)
        return allItems.stream()
                .map(item -> new tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto(
                        item.getId(),
                        item.getName(),
                        item.getUnit(),
                        stockMap.getOrDefault(item.getId(), BigDecimal.ZERO) // 若無快照，預設為 0
                ))
                .collect(Collectors.toList());
    }


    /**
     * 合併主單備註與單項備註
     * 格式範例: "月底盤點 | 破損報廢"
     *
     * @param mainNote 主單備註 (Request Level)
     * @param itemNote 單項備註 (Item Level)
     * @return 合併後的備註字串
     */
    private String buildAuditNote(String mainNote, String itemNote) {
        StringBuilder noteBuilder = new StringBuilder();

        // 1. 處理主單備註
        if (mainNote != null && !mainNote.isEmpty()) {
            noteBuilder.append(mainNote);
        }

        // 2. 處理單項備註
        if (itemNote != null && !itemNote.isEmpty()) {
            if (!noteBuilder.isEmpty()) {
                noteBuilder.append(" | "); // 如果前面已有內容，加上分隔符號
            }
            noteBuilder.append(itemNote);
        }

        return noteBuilder.toString();
    }
}