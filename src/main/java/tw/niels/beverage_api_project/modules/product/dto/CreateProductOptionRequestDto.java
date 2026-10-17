package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * 修改：Lombok 對 boolean isDefault 產生的是 isDefault()／setDefault()，
     * Jackson 會把 JSON 屬性名推成 "default"，前端送出的 "isDefault" 會被忽略（永遠是 false）。
     * 改為明確的存取方法並以 @JsonProperty 固定為 "isDefault"（與回應的 ProductOptionResponseDto 一致），
     * 同時以 @JsonAlias 相容舊的 "default" 寫法。
     */
    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    @JsonAlias("default")
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
