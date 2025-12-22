package tw.niels.beverage_api_project.modules.product.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

@Entity
@Getter
@Setter
@Table(name = "product_options")
@AttributeOverride(name = "id", column = @Column(name = "option_id"))
public class ProductOption extends BaseTsidEntity {

    public ProductOption() {
    }

    @Column(name = "name", nullable = false, length = 50)
    private String optionName;

    @Column(name = "price_adjustment", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAdjustment;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private OptionGroup optionGroup;


}
