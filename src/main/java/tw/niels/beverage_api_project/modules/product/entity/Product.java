package tw.niels.beverage_api_project.modules.product.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="products")
public class Product {
    @Id
    @Column(name="product_id")
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long productId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="brand_id", nullable=false)
    private Brand brandId;

    @Column(name="name", nullable=false, length=100)
    private String name;

    @Lob
    @Column(name="description")
    private String description;

    @Column(name="base_price", nullable=false, precision=10, scale=2)
    private BigDecimal basePrice;

    @Column(name="image_url", length=255)
    private String imageUrl;

    @Column(name="is_available")
    private Boolean isAvailable;

    @ManyToMany(fetch= FetchType.LAZY)
    @JoinTable(
        name= "product_category_mapping",
        joinColumns = @JoinColumn(name="product_id"),
        inverseJoinColumns = @JoinColumn(name="category_id")
    )
    private Set<Category> categories = new HashSet<>();
}
