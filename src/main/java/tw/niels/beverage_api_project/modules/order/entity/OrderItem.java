package tw.niels.beverage_api_project.modules.order.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

@Entity
@Table(name = "order_items")
@AttributeOverride(name = "id", column = @Column(name = "order_item_id"))
@Getter
@Setter
public class OrderItem extends BaseTsidEntity {

    public OrderItem() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "notes")
    private String notes;

    // orderItem custom option
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "order_item_options", joinColumns = @JoinColumn(name = "order_item_id"), inverseJoinColumns = @JoinColumn(name = "option_id"))
    private Set<ProductOption> options = new HashSet<>();

    // 商品快照
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot")
    private ProductSnapshot productSnapshot;


}