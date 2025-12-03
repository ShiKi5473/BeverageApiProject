package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"brand_id", "name"})
})
@AttributeOverride(name = "id", column = @Column(name = "inventory_item_id"))
public class InventoryItem extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit; // 單位 (e.g., "ml", "g", "瓶")

    // 總庫存量 (快取欄位，用於快速檢查與鎖定)
    @Column(name = "total_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    // Constructors
    public InventoryItem() {}

    // Getters & Setters
    public Long getInventoryItemId() { return getId(); }
    public void setInventoryItemId(Long id) { setId(id); }

    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
}