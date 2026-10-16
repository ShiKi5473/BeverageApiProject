package tw.niels.beverage_api_project.modules.report.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "商品銷售統計資料")
public class ProductSalesStatsDto {

    // [P0-10] TSID 超出 JS 安全整數範圍，回應中的 ID 序列化為字串
    @Schema(description = "商品 ID (字串)", example = "101")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String productName;

    @Schema(description = "銷售數量", example = "120")
    private Long totalQuantity;

    @Schema(description = "銷售總額 (小計加總)", example = "6000.00")
    private BigDecimal totalSalesAmount;
}