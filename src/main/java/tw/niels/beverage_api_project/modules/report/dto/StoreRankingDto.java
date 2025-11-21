package tw.niels.beverage_api_project.modules.report.dto;

import java.math.BigDecimal;

public class StoreRankingDto {
    private Long storeId;
    private String storeName; // 這欄位可能不會在 JPQL 建構子中被賦值，而是事後補上
    private BigDecimal totalRevenue;

    // 建構子：對應 Repository JPQL
    public StoreRankingDto(Long storeId, BigDecimal totalRevenue) {
        this.storeId = storeId;
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }

    // Getters and Setters
    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
}