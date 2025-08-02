package tw.niels.beverage_api_project.modules.product.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name="product_options")
public class ProductOption {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="option_id")
    private Long optionId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="brand_id", nullable=false)
    private Brand brand;

    @Column(name="option_name", nullable=false, length=50)
    private String optionName;

    @Column(name="price_adjustment", nullable=false, precision=10, scale=2)
    private BigDecimal priceAdjustment;

    @Column(name="is_default", nullable=false)
    private boolean isDefault;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="group_id", nullable=false)
    private OptionGroup optionGroup;
}
