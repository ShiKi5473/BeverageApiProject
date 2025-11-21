package tw.niels.beverage_api_project.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "商品銷售統計資料")
public class ProductSalesStatsDto {

    @Schema(description = "商品 ID", example = "101")
    private Long productId;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String productName;

    @Schema(description = "銷售數量", example = "120")
    private Long totalQuantity;

    @Schema(description = "銷售總額 (小計加總)", example = "6000.00")
    private BigDecimal totalSalesAmount;
}