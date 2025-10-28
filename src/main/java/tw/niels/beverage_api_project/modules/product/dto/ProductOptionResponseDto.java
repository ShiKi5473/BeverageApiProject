package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

public class ProductOptionResponseDto {
    private Long optionId;
    private String optionName;
    private BigDecimal priceAdjustment;
    private boolean isDefault;

    public static ProductOptionResponseDto fromEntity(ProductOption entity) {
        ProductOptionResponseDto dto = new ProductOptionResponseDto();
        dto.setOptionId(entity.getOptionId());
        dto.setOptionName(entity.getOptionName());
        dto.setPriceAdjustment(entity.getPriceAdjustment());
        dto.setIsDefault(entity.isDefault());
        return dto;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public String getOptionName() {
        return optionName;
    }

    public void setOptionName(String optionName) {
        this.optionName = optionName;
    }

    public BigDecimal getPriceAdjustment() {
        return priceAdjustment;
    }

    public void setPriceAdjustment(BigDecimal priceAdjustment) {
        this.priceAdjustment = priceAdjustment;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
