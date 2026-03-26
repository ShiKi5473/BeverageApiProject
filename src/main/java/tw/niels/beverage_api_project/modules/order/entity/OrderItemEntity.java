package tw.niels.beverage_api_project.modules.order.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;

@Entity
@Table(name = "order_items")
@AttributeOverride(name = "id", column = @Column(name = "order_item_id"))
@Getter
@Setter
public class OrderItemEntity extends BaseTsidEntity {

    public OrderItemEntity() {
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    // 保留 ProductEntity 關聯以方便統計，但在 Service 層會自動從 Variant 帶入
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    // 【新增】關聯到具體的規格 (例如：大杯)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariantEntity productVariant;

    @Column(name = "quantity", nullable = false)
    @NotNull
    @Min(1)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal subtotal;

    @Column(name = "notes")
    private String notes;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "order_item_options", joinColumns = @JoinColumn(name = "order_item_id"), inverseJoinColumns = @JoinColumn(name = "option_id"))
    private Set<ProductOptionEntity> options = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot")
    private ProductSnapshot productSnapshot;
}
