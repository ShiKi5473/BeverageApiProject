package tw.niels.beverage_api_project.modules.inventory.domain.model;

import java.time.LocalDate;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Domain - Entity（屬於 InventoryItem 或獨立管理）
 * ============================================================
 *
 * 庫存批次實體
 *
 * 追蹤特定批次的物料入庫狀況與有效期限。
 * 採購進貨或生產入庫時會產生新的批次。
 *
 * 業務規則：
 * - 必須有關聯的庫存品項 ID。
 * - 必須有有效期限（expiryDate）。
 * - 記錄入庫原始數量與當前剩餘數量。
 * ============================================================
 */
public class InventoryBatch {

    private Long id;
    private Long inventoryItemId;
    private Long storeId;
    private Quantity quantityReceived;
    private Quantity currentQuantity;
    private LocalDate productionDate;
    private LocalDate expiryDate;

    public InventoryBatch(Long id, Long inventoryItemId, Long storeId,
                          Quantity quantityReceived, Quantity currentQuantity,
                          LocalDate productionDate, LocalDate expiryDate) {
        this.id = id;
        this.inventoryItemId = inventoryItemId;
        this.storeId = storeId;
        this.quantityReceived = quantityReceived;
        this.currentQuantity = currentQuantity;
        this.productionDate = productionDate;
        this.expiryDate = expiryDate;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getInventoryItemId() { return inventoryItemId; }
    public Long getStoreId() { return storeId; }
    public Quantity getQuantityReceived() { return quantityReceived; }
    public Quantity getCurrentQuantity() { return currentQuantity; }
    public LocalDate getProductionDate() { return productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    /**
     * 檢查此批次是否已過期。
     *
     * @param today 當前日期
     * @return true 代表已過期
     */
    public boolean isExpired(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }

    /**
     * 扣減批次庫存。
     *
     * @param amount 要扣減的數量
     */
    public void consume(Quantity amount) {
        if (currentQuantity.isLessThan(amount)) {
            throw new IllegalStateException("批次數量不足，無法扣減");
        }
        this.currentQuantity = this.currentQuantity.subtract(amount);
    }
}
