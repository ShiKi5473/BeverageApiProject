package tw.niels.beverage_api_project.modules.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "更新會員點數規則請求")
public class UpdatePointConfigDto {

    @NotNull(message = "累積匯率不可為空")
    @DecimalMin(value = "0.01", message = "累積匯率必須大於 0")
    @Schema(description = "消費多少元獲得 1 點", example = "30.00")
    private BigDecimal earnRate;

    @NotNull(message = "折抵匯率不可為空")
    @DecimalMin(value = "0.01", message = "折抵匯率必須大於 0")
    @Schema(description = "每 1 點可折抵多少元", example = "1.00")
    private BigDecimal redeemRate;

    @Schema(description = "點數過期天數 (null 為永不過期)", example = "365")
    private Integer expiryDays;

}