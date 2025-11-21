// 檔案： .../modules/order/dto/PosCheckoutRequestDto.java
package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 專用於 POS 現場結帳 (一步到位) 的 DTO
 */
@Data
@Schema(description = "POS 快速結帳請求 (一步到位)")
public class PosCheckoutRequestDto {

    @NotEmpty(message = "訂單品項不可為空")
    @Schema(description = "購買品項列表")
    private List<@Valid OrderItemDto> items;

    @Schema(description = "會員 ID (若有會員)", example = "5")
    private Long memberId;

    @Min(value = 0)
    @Schema(description = "使用點數折抵", example = "10")
    private Long pointsToUse = 0L;

    @NotBlank(message = "付款方式不可為空")
    @Schema(description = "付款方式代碼", example = "CASH")
    private String paymentMethod;


}