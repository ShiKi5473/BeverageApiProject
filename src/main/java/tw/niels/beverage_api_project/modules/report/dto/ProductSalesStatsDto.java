package tw.niels.beverage_api_project.modules.report.dto;

import java.math.BigDecimal;

public class ProductSalesStatsDto {
    private Long productId;
    private String productName;
    private Long totalQuantity;
    private BigDecimal totalSalesAmount;

    public ProductSalesStatsDto(Long productId, String productName, Long totalQuantity, BigDecimal totalSalesAmount) {
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity != null ? totalQuantity : 0L;
        this.totalSalesAmount = totalSalesAmount != null ? totalSalesAmount : BigDecimal.ZERO;
    }

    // Getters
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getTotalQuantity() { return totalQuantity; }
    public BigDecimal getTotalSalesAmount() { return totalSalesAmount; }
}