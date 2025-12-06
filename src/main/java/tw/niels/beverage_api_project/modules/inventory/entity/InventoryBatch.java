package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_batches", indexes = {
        @Index(name = "idx_inventory_batches_expiry_date", columnList = "expiry_date"),
        @Index(name = "idx_batches_store_item", columnList = "store_id, inventory_item_id") // V9 新索引
})
@AttributeOverride(name = "id", column = @Column(name = "batch_id"))
public class InventoryBatch extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = true) // 允許為 null (支援非採購來源)
    private PurchaseShipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    // --- V9 新增欄位 ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "quantity_received", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityReceived;

    @Column(name = "current_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentQuantity;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    // Getters & Setters
    public Long getBatchId() { return getId(); }
    public void setBatchId(Long batchId) { setId(batchId); }

    public PurchaseShipment getShipment() { return shipment; }
    public void setShipment(PurchaseShipment shipment) { this.shipment = shipment; }

    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }

    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal quantityReceived) { this.quantityReceived = quantityReceived; }

    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }

    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}