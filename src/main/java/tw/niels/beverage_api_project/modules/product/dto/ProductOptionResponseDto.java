package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

import java.math.BigDecimal;

@Data
@Schema(description = "客製化選項項目 (如: 半糖)")
public class ProductOptionResponseDto {

    @Schema(description = "選項 ID", example = "22")
    private Long optionId;

    @Schema(description = "選項名稱", example = "半糖")
    private String optionName;

    @Schema(description = "價格調整 (正數加價)", example = "0.00")
    private BigDecimal priceAdjustment;

    @Schema(description = "是否為預設值", example = "false")
    private boolean isDefault;

    public static ProductOptionResponseDto fromEntity(ProductOption entity) {
        if (entity == null) return null;

        ProductOptionResponseDto dto = new ProductOptionResponseDto();
        dto.setOptionId(entity.getOptionId());
        dto.setOptionName(entity.getOptionName());
        dto.setPriceAdjustment(entity.getPriceAdjustment());
        dto.setDefault(entity.isDefault());
        return dto;
    }
}