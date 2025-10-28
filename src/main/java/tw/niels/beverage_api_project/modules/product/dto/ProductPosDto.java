package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;

public class ProductPosDto {
    private Long id;

    private Long name;

    private BigDecimal basePrice;

    Set<OptionGroupResponseDto> optionGroups;

    public ProductPosDto(BigDecimal basePrice, Long id, Long name, Set<OptionGroupResponseDto> optionGroups) {
        this.basePrice = basePrice;
        this.id = id;
        this.name = name;
        this.optionGroups = optionGroups;
    }

    public ProductPosDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getName() {
        return name;
    }

    public void setName(Long name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Set<OptionGroupResponseDto> getOptionGroups() {
        return optionGroups;
    }

    public void setOptionGroups(Set<OptionGroupResponseDto> optionGroups) {
        this.optionGroups = optionGroups;
    }
}
