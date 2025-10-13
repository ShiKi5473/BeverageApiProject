package tw.niels.beverage_api_project.modules.order.dto;

import java.math.BigDecimal;

public class OrderTotalDto {

    private BigDecimal totalAmount;

    public OrderTotalDto(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    // Getter and Setter
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
