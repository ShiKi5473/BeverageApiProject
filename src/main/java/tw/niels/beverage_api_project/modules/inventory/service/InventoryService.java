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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final PurchaseShipmentRepository shipmentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ControllerHelperService helperService;

    public InventoryService(InventoryItemRepository itemRepository,
                            InventoryBatchRepository batchRepository,
                            PurchaseShipmentRepository shipmentRepository,
                            StoreRepository storeRepository,
                            UserRepository userRepository,
                            ControllerHelperService helperService) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
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
     * 採用「總量預扣」策略，先鎖定 Item 防止死鎖，再扣減批次。
     * * @param brandId 品牌 ID (用於驗證)
     * @param storeId 店家 ID
     * @param itemId 原物料 ID
     * @param quantityToDeduct 要扣減的數量
     */
    @Transactional
    public void deductInventory(Long brandId, Long storeId, Long itemId, BigDecimal quantityToDeduct) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("扣減數量必須大於 0");
        }

        // 1. 先鎖定原物料 (Item Level Lock)
        // 這會序列化同一品項的扣減請求，避免多個交易同時搶佔不同 Batch 造成的 Deadlock
        InventoryItem item = itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到原物料 ID: " + itemId));

        // 2.檢查總庫存是否足夠
        if (item.getTotalQuantity().compareTo(quantityToDeduct) < 0) {
            throw new BadRequestException(
                    String.format("庫存不足 (Item: %s)。目前: %s, 需求: %s",
                            item.getName(), item.getTotalQuantity(), quantityToDeduct));
        }

        // 3. 預扣總庫存
        item.setTotalQuantity(item.getTotalQuantity().subtract(quantityToDeduct));

        itemRepository.save(item);

        // 4. 執行 FIFO 批次扣減
        // 因為我們已經鎖定了 Item (Parent)，此時去鎖定 Batches (Children) 是安全的
        List<InventoryBatch> batches = batchRepository.findAvailableBatchesForUpdate(storeId, itemId);

        BigDecimal remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                break; // 已扣完
            }

            BigDecimal currentQty = batch.getCurrentQuantity();

            if (currentQty.compareTo(remainingToDeduct) >= 0) {
                // 情況 A: 此批次庫存足夠
                batch.setCurrentQuantity(currentQty.subtract(remainingToDeduct));
                remainingToDeduct = BigDecimal.ZERO;
            } else {
                // 情況 B: 此批次庫存不足，全部扣光，繼續扣下一批
                remainingToDeduct = remainingToDeduct.subtract(currentQty);
                batch.setCurrentQuantity(BigDecimal.ZERO);
            }
            batchRepository.save(batch);
        }

        // 5. 二次檢查 (理論上步驟 2 已經擋掉，但防禦性編程)
        if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
            // 這代表資料庫有資料不一致 (TotalQuantity > Sum(Batches))
            // 發生此情況應拋出系統錯誤，並可能觸發警報
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

    // 為了讓 Controller 能正確呼叫，這裡重載一個方法 (補上 brandId 參數)
    // 原本的 deductInventory 是給手動測試用的
    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantity) {
        // 為了相容舊 API，這裡先查出 brandId (需從 store 查)
        Store store = storeRepository.findByIdSystem(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        deductInventory(store.getBrand().getBrandId(), storeId, itemId, quantity);
    }
}