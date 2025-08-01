package tw.niels.beverage_api_project.modules.product;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    private Long brandId;

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
}
