package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "建立選項項目請求 (如: 去冰、少冰)")
public class CreateProductOptionRequestDto {

    @NotBlank(message = "選項名稱不可為空")
    @Schema(description = "選項名稱", example = "去冰")
    private String optionName;

    @NotNull(message = "價格調整不可為空")
    @Schema(description = "價格調整 (正數加價，負數折價，0為不變)", example = "0.00")
    private BigDecimal priceAdjustment;

    @Schema(description = "是否為預設選項", example = "false")
    private boolean isDefault = false;

}
