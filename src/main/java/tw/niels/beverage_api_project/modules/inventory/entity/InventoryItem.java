package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

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
}