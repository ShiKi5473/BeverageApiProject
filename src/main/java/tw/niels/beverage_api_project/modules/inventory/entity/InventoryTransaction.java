package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_transactions")
@AttributeOverride(name = "id", column = @Column(name = "transaction_id"))
public class InventoryTransaction extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "change_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal changeAmount;

    // 異動原因: RESTOCK(進貨), AUDIT(盤點差異), WASTE(報廢), USAGE(理論耗用-批次處理)
    @Column(name = "reason_type", nullable = false, length = 50)
    private String reasonType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter; // V10 新增

    // Getters & Setters
    public Long getTransactionId() { return getId(); }
    public void setTransactionId(Long id) { setId(id); }

    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }

    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount; }

    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }

    public User getOperator() { return operator; }
    public void setOperator(User operator) { this.operator = operator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
}