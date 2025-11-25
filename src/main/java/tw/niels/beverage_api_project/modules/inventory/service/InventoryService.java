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

        // 2. 建立庫存批次 (Batches)
        for (AddShipmentRequestDto.BatchItemDto itemDto : request.getItems()) {
            InventoryItem item = itemRepository.findByBrand_IdAndId(brandId, itemDto.getInventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到原物料 ID: " + itemDto.getInventoryItemId()));

            // 簡單檢查品牌是否正確
            if (!item.getBrand().getBrandId().equals(brandId)) {
                throw new BadRequestException("原物料不屬於此品牌: " + item.getName());
            }

            InventoryBatch batch = new InventoryBatch();
            batch.setShipment(shipment);
            batch.setInventoryItem(item);
            batch.setQuantityReceived(itemDto.getQuantity());
            batch.setCurrentQuantity(itemDto.getQuantity()); // 初始剩餘量 = 進貨量
            batch.setExpiryDate(itemDto.getExpiryDate());

            batchRepository.save(batch);
        }
        return shipment;
    }

    /**
     * 核心 FIFO 庫存扣減邏輯 (Deduct Inventory)
     * @param storeId 店家 ID
     * @param itemId 原物料 ID
     * @param quantityToDeduct 要扣減的數量
     */
    @Transactional
    public void deductInventory(Long storeId, Long itemId, BigDecimal quantityToDeduct) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("扣減數量必須大於 0");
        }

        // 1. 查詢可用批次 (已加悲觀鎖，並依效期由舊到新排序)
        List<InventoryBatch> batches = batchRepository.findAvailableBatchesForUpdate(storeId, itemId);

        BigDecimal remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                break; // 已扣完
            }

            BigDecimal currentQty = batch.getCurrentQuantity();

            if (currentQty.compareTo(remainingToDeduct) >= 0) {
                // 情況 A: 此批次庫存足夠
                // 剩餘庫存 = 原庫存 - 需扣減量
                batch.setCurrentQuantity(currentQty.subtract(remainingToDeduct));
                remainingToDeduct = BigDecimal.ZERO;
            } else {
                // 情況 B: 此批次庫存不足，全部扣光，繼續扣下一批
                // 需扣減量 = 原需扣減量 - 此批次庫存
                remainingToDeduct = remainingToDeduct.subtract(currentQty);
                batch.setCurrentQuantity(BigDecimal.ZERO);
            }
            batchRepository.save(batch);
        }

        // 2. 檢查是否扣減成功 (若 remainingToDeduct > 0 代表總庫存不足)
        if (remainingToDeduct.compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("庫存不足 (Store: " + storeId + ", Item: " + itemId + ")，短缺: " + remainingToDeduct);
        }
    }

    /**
     * 查詢當前總庫存
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentStock(Long storeId, Long itemId) {
        return batchRepository.sumQuantityByStoreAndItem(storeId, itemId)
                .orElse(BigDecimal.ZERO);
    }
}