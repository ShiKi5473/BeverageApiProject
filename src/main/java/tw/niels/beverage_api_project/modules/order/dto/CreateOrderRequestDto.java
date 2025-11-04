package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

public class CreateOrderRequestDto {


    @NotNull(message = "訂單狀態不可為空")
    private OrderStatus status;

// staffId is from jwtToken

    @NotEmpty(message = "訂單品項不可為空")
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
