package tw.niels.beverage_api_project.modules.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ProcessPaymentRequestDto {

    private Long memberId; //

    @Min(value = 0)
    private Long pointsToUse = 0L;

    @NotBlank(message = "付款方式不可為空")
    private String paymentMethod;

    // Getters and Setters
    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getPointsToUse() {
        return pointsToUse;
    }

    public void setPointsToUse(Long pointsToUse) {
        this.pointsToUse = pointsToUse;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}