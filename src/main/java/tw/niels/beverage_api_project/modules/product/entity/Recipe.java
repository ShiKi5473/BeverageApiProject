package tw.niels.beverage_api_project.modules.product.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recipes")
@AttributeOverride(name = "id", column = @Column(name = "recipe_id"))
public class Recipe extends BaseTsidEntity {

    // 關聯到規格 (Nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    // 關聯到加料選項 (Nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private ProductOption option;

    // 要扣減的原料
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    // Getters & Setters
    public Long getRecipeId() { return getId(); }
    public void setRecipeId(Long id) { setId(id); }

    public ProductVariant getVariant() { return variant; }
    public void setVariant(ProductVariant variant) { this.variant = variant; }

    public ProductOption getOption() { return option; }
    public void setOption(ProductOption option) { this.option = option; }

    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}