package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.entity.PurchaseShipment;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.PurchaseShipmentRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;

import java.math.BigDecimal;
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

    public InventoryService(InventoryItemRepository itemRepository,
                            InventoryBatchRepository batchRepository,
                            InventoryBatchDAO inventoryBatchDAO,
                            PurchaseShipmentRepository shipmentRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            ControllerHelperService helperService) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.inventoryBatchDAO = inventoryBatchDAO;
        this.shipmentRepository = shipmentRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.helperService = helperService;
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
     * 查詢當前總庫存 (改為直接查 Item 表，不需加總 Batch，效能更好)
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
//        TODO 修正多店架構下的庫存問題
        // 注意：這裡假設一個 Item 只屬於一個 Store 的庫存管理範疇，
        // 但目前的架構 InventoryItem 是 Brand 層級的定義，庫存是跟著 Store (透過 Batch)
        // 為了支援「分店庫存」，我們目前的 total_quantity 是存在 InventoryItem (Brand 層級) 上，
        // 這在「單一品牌單一倉庫」沒問題，但在「多店」架構下，InventoryItem 的 total_quantity 會變成「全品牌總庫存」。

        // 【修正邏輯】
        // 由於 InventoryItem 是品牌共用的「定義」，我們不能把分店的庫存寫在 InventoryItem 上。
        // 如果要優化「分店」庫存查詢，應該要有一張 `StoreInventory` 表。
        // 但基於目前架構 (Batch 關聯 Store)，我們還是得維持 sumQuantityByStoreAndItem。
        // 上面新增的 total_quantity 其實變成了「全品牌該物料總數」(若有此需求)。

        // 暫時維持舊邏輯以確保分店資料正確：
        return batchRepository.sumQuantityByStoreAndItem(storeId, itemId)
                .orElse(BigDecimal.ZERO);
    }


    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantity) {
        Store store = storeRepository.findByIdSystem(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.resource.not_found", "Store ID: " + storeId));
        deductInventory(store.getBrand().getBrandId(), storeId, itemId, quantity);
    }
}