package tw.niels.beverage_api_project.modules.inventory.domain.model;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Domain - Entity
 * ============================================================
 *
 * 庫存異動記錄實體
 *
 * 記錄所有庫存增減的歷史資訊，用於對帳與審核。
 *
 * 職責：
 * 1. 記錄異動原因（進貨、損耗、盤點、耗用）。
 * 2. 記錄異動前後的水位變化。
 * ============================================================
 */
public class InventoryTransaction {

    private Long id;
    private final Long inventoryItemId;
    private final Long storeId;
    private final Quantity changeAmount;
    private final Quantity balanceAfter;
    private final String reasonType;
    private final Long operatorId;
    private final String note;
    private final Instant createdAt;

    public InventoryTransaction(Long id, Long inventoryItemId, Long storeId,
                                Quantity changeAmount, Quantity balanceAfter,
                                String reasonType, Long operatorId, String note) {
        this.id = id;
        this.inventoryItemId = inventoryItemId;
        this.storeId = storeId;
        this.changeAmount = changeAmount;
        this.balanceAfter = balanceAfter;
        this.reasonType = reasonType;
        this.operatorId = operatorId;
        this.note = note;
        this.createdAt = Instant.now();
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getInventoryItemId() { return inventoryItemId; }
    public Long getStoreId() { return storeId; }
    public Quantity getChangeAmount() { return changeAmount; }
    public Quantity getBalanceAfter() { return balanceAfter; }
    public String getReasonType() { return reasonType; }
    public Long getOperatorId() { return operatorId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
