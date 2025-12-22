package tw.niels.beverage_api_project.modules.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "product_variants")
@AttributeOverride(name = "id", column = @Column(name = "variant_id"))
public class ProductVariant extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String name; // e.g. "中杯", "大杯"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "sku_code", length = 50)
    private String skuCode;

}