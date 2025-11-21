package tw.niels.beverage_api_project.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "品牌銷售總覽資料")
public class BrandSalesSummaryDto {

    @Schema(description = "區間內總訂單數", example = "150")
    private Long totalOrders;

    @Schema(description = "區間內總營收 (未扣折扣)", example = "50000.00")
    private BigDecimal totalRevenue;

    @Schema(description = "區間內總折扣金額", example = "5000.00")
    private BigDecimal totalDiscount;

    @Schema(description = "區間內取消訂單數", example = "3")
    private Long cancelledOrders;

    public BrandSalesSummaryDto(Long totalOrders, BigDecimal totalRevenue, BigDecimal totalDiscount, Long cancelledOrders) {
        this.totalOrders = totalOrders != null ? totalOrders : 0L;
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        this.totalDiscount = totalDiscount != null ? totalDiscount : BigDecimal.ZERO;
        this.cancelledOrders = cancelledOrders != null ? cancelledOrders : 0L;
    }

    @Schema(description = "區間內實收金額 (總營收 - 總折扣)", example = "45000.00")
    public BigDecimal getFinalRevenue() {
        return totalRevenue.subtract(totalDiscount);
    }
}