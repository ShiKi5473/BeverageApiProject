package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderItemDto {
    @NotNull(message = "商品ID不可為空")
    private Long productId;

    @NotNull(message = "商品數量不可為空")
    @Min(value = 1, message = "數量至少為1")
    private Integer quantity;

    private String notes;

    private List<Long> optionIds;

    // getter and setter
    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<Long> getOptionIds() {
        return optionIds;
    }

    public void setOptionIds(List<Long> optionIds) {
        this.optionIds = optionIds;
    }

}
