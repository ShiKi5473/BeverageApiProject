package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Schema(description = "建立訂單請求")
public class CreateOrderRequestDto {

    @NotNull(message = "訂單狀態不可為空")
    @Schema(description = "初始狀態 (通常為 PENDING 或 HELD)", example = "PENDING")
    private OrderStatus status;

    @NotEmpty(message = "訂單品項不可為空")
    @Schema(description = "購買品項列表")
    private List<@Valid OrderItemDto> items;

    public CreateOrderRequestDto() {
    }

    // getter and setter

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }


    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

}
