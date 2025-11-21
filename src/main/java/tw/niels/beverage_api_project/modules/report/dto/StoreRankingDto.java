package tw.niels.beverage_api_project.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "分店營收排行資料")
public class StoreRankingDto {

    @Schema(description = "分店 ID", example = "1")
    private Long storeId;

    @Schema(description = "分店名稱", example = "台北信義店")
    private String storeName;

    @Schema(description = "總實收金額", example = "120000.00")
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