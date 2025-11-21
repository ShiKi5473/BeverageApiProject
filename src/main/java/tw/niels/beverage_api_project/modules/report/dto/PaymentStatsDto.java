package tw.niels.beverage_api_project.modules.report.dto;

import java.math.BigDecimal;

public class PaymentStatsDto {
    private String paymentCode;
    private BigDecimal totalAmount;

    public PaymentStatsDto(String paymentCode, BigDecimal totalAmount) {
        this.paymentCode = paymentCode != null ? paymentCode : "UNKNOWN";
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    // Getters
    public String getPaymentCode() { return paymentCode; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}