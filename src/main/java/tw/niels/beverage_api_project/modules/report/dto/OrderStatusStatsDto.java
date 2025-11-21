package tw.niels.beverage_api_project.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "訂單狀態統計資料")
public class OrderStatusStatsDto {

    @Schema(description = "訂單狀態", example = "CLOSED")
    private OrderStatus status;

    @Schema(description = "該狀態訂單數量", example = "50")
    private Long count;

    @Schema(description = "原始總金額 (未扣折扣)", example = "2500.00")
    private BigDecimal totalAmount;

    @Schema(description = "折扣總金額", example = "200.00")
    private BigDecimal discountAmount;

    @Schema(description = "實收總金額", example = "2300.00")
    private BigDecimal finalAmount;
}