package tw.niels.beverage_api_project.modules.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
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

}