package tw.niels.beverage_api_project.modules.report.dto;

import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import java.math.BigDecimal;

public class OrderStatusStatsDto {
    private OrderStatus status;
    private Long count;
    private BigDecimal totalAmount;    // 原始金額
    private BigDecimal discountAmount; // 折扣金額
    private BigDecimal finalAmount;    // 實收金額

    public OrderStatusStatsDto(OrderStatus status, Long count, BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal finalAmount) {
        this.status = status;
        this.count = count != null ? count : 0L;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.finalAmount = finalAmount != null ? finalAmount : BigDecimal.ZERO;
    }

    // Getters
    public OrderStatus getStatus() { return status; }
    public Long getCount() { return count; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
}