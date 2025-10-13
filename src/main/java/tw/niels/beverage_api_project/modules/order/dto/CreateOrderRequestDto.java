package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CreateOrderRequestDto {

    @NotNull(message = "店家ID不可為空")
    private Long storeId;

    private Long memberId; // 會員 ID，可選

    // staffId is from jwtToken

    @NotEmpty(message="訂單品項不可為空")
    private List<@Valid OrderItemDto> items;

    // getter and setter
    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }
}
