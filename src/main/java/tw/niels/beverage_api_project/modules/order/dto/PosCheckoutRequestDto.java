// 檔案： .../modules/order/dto/PosCheckoutRequestDto.java
package tw.niels.beverage_api_project.modules.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 專用於 POS 現場結帳 (一步到位) 的 DTO
 */
public class PosCheckoutRequestDto {

    @NotEmpty(message = "訂單品項不可為空")
    private List<@Valid OrderItemDto> items; // 複用 OrderItemDto

    private Long memberId; // 複用 ProcessPaymentRequestDto 的欄位

    @Min(value = 0)
    private Long pointsToUse = 0L; // 複用 ProcessPaymentRequestDto 的欄位

    @NotBlank(message = "付款方式不可為空")
    private String paymentMethod; // 複用 ProcessPaymentRequestDto 的欄位

    // --- Getters and Setters ---

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getPointsToUse() { return pointsToUse; }
    public void setPointsToUse(Long pointsToUse) { this.pointsToUse = pointsToUse; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}