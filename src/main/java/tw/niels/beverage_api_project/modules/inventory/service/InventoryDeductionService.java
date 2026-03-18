package tw.niels.beverage_api_project.modules.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

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
 *   <li>先鎖定 InventoryItem (悲觀鎖)，檢查總庫存是否足夠 (Fast Fail)。</li>
 *   <li>查詢該品項在該分店的所有可用批次，依到期日升序排列。</li>
 *   <li>從最早到期的批次開始扣減：若該批次數量 >= 剩餘待扣量，直接扣除；否則扣光該批次，繼續扣下一批。</li>
 *   <li>使用 JDBC Batch Update 一次性寫入所有批次的新數量，減少資料庫交互。</li>
 * </ol>
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

    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBatchDAO inventoryBatchDAO;
    private final StoreRepository storeRepository;

    public InventoryDeductionService(InventoryItemRepository itemRepository,
                                     InventoryBatchRepository batchRepository,
                                     InventoryBatchDAO inventoryBatchDAO,
                                     StoreRepository storeRepository) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.inventoryBatchDAO = inventoryBatchDAO;
        this.storeRepository = storeRepository;
    }

    /**
     * 核心 FIFO 庫存扣減邏輯 (單品項)
     * 流程：鎖定品項 → 檢查總量 → 預扣總量 → FIFO 批次扣減 → JDBC 批次寫入
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
                    MSG_INSUFFICIENT_STOCK,
                    item.getName(),
                    item.getTotalQuantity(),
                    quantityToDeduct
            );
        }

        // 3. 預扣總庫存 (Item Table) - 單筆更新保留 JPA
        item.setTotalQuantity(item.getTotalQuantity().subtract(quantityToDeduct));
        itemRepository.save(item);

        // 4. FIFO 批次扣減計算（依到期日升序，優先扣減即將過期的批次）
        List<InventoryBatch> batches = batchRepository.findAvailableBatchesForUpdate(storeId, itemId);

        List<InventoryBatchDAO.BatchUpdateTuple> batchUpdates = new ArrayList<>();
        BigDecimal remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {
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
            throw new IllegalStateException("庫存資料異常：總量檢查通過但批次不足。短缺: " + remainingToDeduct);
        }
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
     * 批次處理多品項庫存扣減 (供盤點盤損使用)
     * 與單品項扣減不同，此方法一次鎖定多個 Item 並批次計算 FIFO，減少 DB 交互次數。
     *
     * @param brandId      品牌 ID
     * @param storeId      分店 ID
     * @param deductionMap 待扣減的品項與數量 (Map&lt;ItemId, Quantity&gt;)
     */
    @Transactional
    public void processBatchDeductions(Long brandId, Long storeId, Map<Long, BigDecimal> deductionMap) {
        Set<Long> itemIds = deductionMap.keySet();

        // A. 批次鎖定 InventoryItem (防止並發修改總量)
        List<InventoryItem> itemsToUpdate = itemRepository.findByBrandIdAndIdInForUpdate(brandId, itemIds);
        Map<Long, InventoryItem> lockedItemMap = itemsToUpdate.stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        // B. 更新 Item 總庫存 (檢查 + 扣減)
        for (Long itemId : itemIds) {
            InventoryItem item = lockedItemMap.get(itemId);
            BigDecimal qtyToDeduct = deductionMap.get(itemId);

            if (item == null) {
                throw new ResourceNotFoundException("Item not found during deduction: " + itemId);
            }

            // 檢查總量是否足夠 (Fast Fail)
            if (item.getTotalQuantity().compareTo(qtyToDeduct) < 0) {
                throw new BadRequestException(
                        MSG_INSUFFICIENT_STOCK,
                        item.getName(),
                        item.getTotalQuantity(),
                        qtyToDeduct
                );
            }

            item.setTotalQuantity(item.getTotalQuantity().subtract(qtyToDeduct));
        }
        itemRepository.saveAll(itemsToUpdate);

        // C. 批次查詢所有相關的可用 Batches
        List<InventoryBatch> allBatches = batchRepository.findAvailableBatchesForItems(storeId, itemIds);

        // 將 Batches 依照 Item 分組
        Map<Long, List<InventoryBatch>> batchesByItem = allBatches.stream()
                .collect(Collectors.groupingBy(b -> b.getInventoryItem().getId()));

        // 準備 JDBC Batch Update 列表
        List<InventoryBatchDAO.BatchUpdateTuple> batchUpdates = new ArrayList<>();

        // D. 記憶體內計算 FIFO 扣減（每個品項各自依到期日順序扣減）
        for (Long itemId : itemIds) {
            BigDecimal remainingToDeduct = deductionMap.get(itemId);
            List<InventoryBatch> itemBatches = batchesByItem.getOrDefault(itemId, Collections.emptyList());

            for (InventoryBatch batch : itemBatches) {
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
                throw new IllegalStateException("庫存資料不一致：Item總量檢查通過，但實際批次總和不足。Item ID: " + itemId + ", 短缺: " + remainingToDeduct);
            }
        }

        // E. 執行 JDBC 批次更新
        if (!batchUpdates.isEmpty()) {
            inventoryBatchDAO.batchUpdateQuantities(batchUpdates);
        }
    }
}
