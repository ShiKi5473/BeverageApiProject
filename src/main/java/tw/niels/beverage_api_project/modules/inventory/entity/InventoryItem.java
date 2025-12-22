package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"brand_id", "name"})
})
@AttributeOverride(name = "id", column = @Column(name = "inventory_item_id"))
@Getter
@Setter
public class InventoryItem extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    // --- V9 新增欄位 ---

    @Column(name = "cost_per_unit", precision = 10, scale = 4)
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Column(name = "safety_stock")
    private Integer safetyStock = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // --- 原有欄位 (total_quantity) 仍保留，但語義可能轉變為"參考庫存" ---
    @Column(name = "total_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    // Constructors, Getters & Setters
    public InventoryItem() {}

}