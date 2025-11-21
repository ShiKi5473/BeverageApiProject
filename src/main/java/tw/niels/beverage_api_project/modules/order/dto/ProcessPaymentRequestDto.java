package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "訂單結帳/付款請求")
public class ProcessPaymentRequestDto {

    @Schema(description = "會員 ID (綁定會員)", example = "5")
    private Long memberId;

    @Min(value = 0)
    @Schema(description = "使用點數折抵", example = "0")
    private Long pointsToUse = 0L;

    @NotBlank(message = "付款方式不可為空")
    @Schema(description = "付款方式代碼 (CASH, LINE_PAY...)", example = "CREDIT_CARD")
    private String paymentMethod;


}