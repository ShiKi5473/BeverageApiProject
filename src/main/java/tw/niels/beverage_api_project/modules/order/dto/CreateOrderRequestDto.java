package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CreateOrderRequestDto {

    @NotNull(message = "店家ID不可為空")
    private Long storeId;

    private String paymentMethod;

    // staffId is from jwtToken

    @NotEmpty(message = "訂單品項不可為空")
    private List<@Valid OrderItemDto> items;

    public CreateOrderRequestDto() {
    }

    // getter and setter
    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

}
