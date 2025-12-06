package tw.niels.beverage_api_project.modules.product.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

import java.math.BigDecimal;

@Entity
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

    // Getters & Setters
    public Long getVariantId() { return getId(); }
    public void setVariantId(Long id) { setId(id); }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
}