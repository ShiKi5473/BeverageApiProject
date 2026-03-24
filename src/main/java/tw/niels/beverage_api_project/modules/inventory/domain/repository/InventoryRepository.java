package tw.niels.beverage_api_project.modules.inventory.domain.repository;

import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.domain.model.InventoryTransaction;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 庫存倉儲介面
 *
 * 定義對庫存品項、批次與異動記錄的存取規範。
 * ============================================================
 */
public interface InventoryRepository {

    // ─── InventoryItem 相關 ─────────────────────────────────────────────

    Optional<InventoryItem> findItemById(Long brandId, Long itemId);
    List<InventoryItem> findActiveItemsByBrand(Long brandId);
    void saveItem(InventoryItem item);

    // ─── InventoryBatch 相關 ────────────────────────────────────────────

    List<InventoryBatch> findBatchesByStoreAndItem(Long storeId, Long itemId);
    void saveBatch(InventoryBatch batch);

    // ─── InventoryTransaction 相關 ──────────────────────────────────────

    void saveTransaction(InventoryTransaction transaction);
    List<InventoryTransaction> findRecentTransactions(Long storeId, Long itemId, int limit);
}
