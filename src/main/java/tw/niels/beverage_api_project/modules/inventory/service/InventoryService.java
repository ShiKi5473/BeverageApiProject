package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
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

        // 2. 建立庫存批次 (Batches) 並更新總庫存
        for (AddShipmentRequestDto.BatchItemDto itemDto : request.getItems()) {
            // 使用 Lock 鎖定 Item，確保計算總量時不會有併發問題
            InventoryItem item = itemRepository.findByBrandIdAndIdForUpdate(brandId, itemDto.getInventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到原物料 ID: " + itemDto.getInventoryItemId()));

            InventoryBatch batch = new InventoryBatch();
            batch.setShipment(shipment);
            batch.setInventoryItem(item);
            batch.setQuantityReceived(itemDto.getQuantity());
            batch.setCurrentQuantity(itemDto.getQuantity()); // 初始剩餘量 = 進貨量
            batch.setExpiryDate(itemDto.getExpiryDate());

            batchRepository.save(batch);

            // 【新增】同步增加總庫存量
            item.setTotalQuantity(item.getTotalQuantity().add(itemDto.getQuantity()));
            itemRepository.save(item);
        }
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
            batchUpdates.add(new tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO.BatchUpdateTuple(batch.getBatchId(), newQty));
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

    /**
     * 執行手動盤點 (Audit Inventory)
     * 邏輯：比較「盤點值」與「目前快照值」，計算差異並寫入異動紀錄，最後更新快照。
     */
    @Transactional
    public void performAudit(Long brandId, Long storeId, InventoryAuditRequestDto request) {
        Store store = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));

        Long operatorId = helperService.getCurrentUserId();
        User operator = userRepository.findByBrand_IdAndId(brandId, operatorId)
                .orElse(null); // 允許 null (如果是系統排程觸發，雖然這裡是 API 觸發)

        for (InventoryAuditRequestDto.AuditItemDto itemDto : request.getItems()) {
            InventoryItem item = itemRepository.findByBrand_IdAndId(brandId, itemDto.getInventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemDto.getInventoryItemId()));

            // 1. 取得目前快照 (若無則視為 0)
            InventorySnapshot snapshot = snapshotRepository
                    .findByStore_StoreIdAndInventoryItem_InventoryItemId(storeId, item.getInventoryItemId())
                    .orElse(new InventorySnapshot());

            // 若是新建立的 Snapshot，需設定關聯
            if (snapshot.getSnapshotId() == null) {
                snapshot.setStore(store);
                snapshot.setInventoryItem(item);
                snapshot.setQuantity(BigDecimal.ZERO);
            }

            BigDecimal currentQty = snapshot.getQuantity();
            BigDecimal actualQty = itemDto.getActualQuantity();
            BigDecimal diff = actualQty.subtract(currentQty);

            // 2. 寫入異動紀錄 (即使 diff 為 0 也要記錄，代表確認過庫存)
            InventoryTransaction trx = new InventoryTransaction();
            trx.setStore(store);
            trx.setInventoryItem(item);
            trx.setChangeAmount(diff);
            trx.setReasonType("AUDIT"); // 標記為盤點調整
            trx.setOperator(operator);

            // 備註組合：總備註 + 單項備註
            String note = (request.getNote() == null ? "" : request.getNote()) +
                    (itemDto.getItemNote() == null ? "" : " (" + itemDto.getItemNote() + ")");
            // 截斷過長的備註以免爆欄位
            if (note.length() > 255) note = note.substring(0, 255);
            trx.setNote(note);

            transactionRepository.save(trx);

            // 3. 更新快照
            snapshot.setQuantity(actualQty);
            snapshot.setLastCheckedAt(Instant.now());
            snapshotRepository.save(snapshot);
        }
    }



    /**
     * 查詢即時庫存 (改寫 getCurrentStock)
     * Phase 3 修改：優先從 Snapshot 讀取，若無則回傳 0
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
        // 從新的 Snapshot 表讀取
        return snapshotRepository.findByStore_StoreIdAndInventoryItem_InventoryItemId(storeId, itemId)
                .map(InventorySnapshot::getQuantity)
                .orElse(BigDecimal.ZERO);
    }


    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantity) {
        Store store = storeRepository.findByIdSystem(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Store ID: " + storeId));
        deductInventory(store.getBrand().getBrandId(), storeId, itemId, quantity);
    }
}