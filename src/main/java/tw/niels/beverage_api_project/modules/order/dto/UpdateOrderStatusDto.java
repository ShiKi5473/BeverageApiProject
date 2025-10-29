package tw.niels.beverage_api_project.modules.order.dto;

import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

public class UpdateOrderStatusDto {
    @NotNull(message = "訂單狀態不可為空")
    private OrderStatus status;

    // 無參數建構子
    public UpdateOrderStatusDto() {
    }

    // Getter
    public OrderStatus getStatus() {
        return status;
    }

    // Setter
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
