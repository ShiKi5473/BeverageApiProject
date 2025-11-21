package tw.niels.beverage_api_project.modules.report.dto;

import java.math.BigDecimal;

public class BrandSalesSummaryDto {
    private Long totalOrders;
    private BigDecimal totalRevenue;   // 原始總額
    private BigDecimal totalDiscount;  // 折扣總額
    private Long cancelledOrders;

    // 建構子：參數順序必須嚴格對應 Repository JPQL 中的 SELECT new ...(...)
    public BrandSalesSummaryDto(Long totalOrders, BigDecimal totalRevenue, BigDecimal totalDiscount, Long cancelledOrders) {
        this.totalOrders = totalOrders != null ? totalOrders : 0L;
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        this.totalDiscount = totalDiscount != null ? totalDiscount : BigDecimal.ZERO;
        this.cancelledOrders = cancelledOrders != null ? cancelledOrders : 0L;
    }

    // Getters (通常報表 DTO 是唯讀的，不需要 Setters，除非您有後續處理需求)
    public Long getTotalOrders() {
        return totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public BigDecimal getTotalDiscount() {
        return totalDiscount;
    }

    public Long getCancelledOrders() {
        return cancelledOrders;
    }

    // 如果需要計算 "實收金額 (Net Revenue)"，可以在這裡加一個 getter
    public BigDecimal getFinalRevenue() {
        return totalRevenue.subtract(totalDiscount);
    }
}