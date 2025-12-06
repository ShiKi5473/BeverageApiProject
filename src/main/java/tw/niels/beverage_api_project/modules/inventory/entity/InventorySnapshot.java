package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_snapshots", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"store_id", "inventory_item_id"})
})
@AttributeOverride(name = "id", column = @Column(name = "snapshot_id"))
public class InventorySnapshot extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    // Getters & Setters
    public Long getSnapshotId() { return getId(); }
    public void setSnapshotId(Long id) { setId(id); }

    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}