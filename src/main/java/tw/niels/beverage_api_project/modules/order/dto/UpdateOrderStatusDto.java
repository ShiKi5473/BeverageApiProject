package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Data
@Schema(description = "更新訂單狀態請求")
public class UpdateOrderStatusDto {

    @NotNull(message = "訂單狀態不可為空")
    @Schema(description = "新的訂單狀態", example = "READY_FOR_PICKUP")
    private OrderStatus status;



}
