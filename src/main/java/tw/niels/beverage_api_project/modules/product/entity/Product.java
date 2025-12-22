package tw.niels.beverage_api_project.modules.product.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Entity
@Getter
@Setter
@Table(name = "products")
@AttributeOverride(name = "id", column = @Column(name = "product_id"))
public class Product extends BaseTsidEntity {

    public Product() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "status_code")
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_category_mappings", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_option_group_mappings", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<OptionGroup> optionGroups = new HashSet<>();


}
