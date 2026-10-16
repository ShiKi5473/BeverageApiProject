package tw.niels.beverage_api_project.modules.report.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "庫存耗損分析報告")
public class InventoryVarianceReportDto {

    // [P0-10] 回應中的 ID 序列化為字串，避免 JS 精度遺失
    @Schema(description = "原物料 ID (字串)")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    @Schema(description = "原物料名稱")
    private String itemName;

    @Schema(description = "單位")
    private String unit;

    @Schema(description = "期初庫存 (Opening)")
    private BigDecimal openingQuantity;

    @Schema(description = "期間進貨 (Restock)")
    private BigDecimal restockQuantity;

    @Schema(description = "期末庫存 (Closing)")
    private BigDecimal closingQuantity;

    @Schema(description = "實際消耗 (Actual Usage = Opening + Restock - Closing)")
    private BigDecimal actualUsage;

    @Schema(description = "理論消耗 (Theoretical Usage - 來自配方)")
    private BigDecimal theoreticalUsage;

    @Schema(description = "耗損差異 (Variance = Actual - Theoretical)")
    private BigDecimal variance;

    @Schema(description = "耗損率 % (Variance / Theoretical * 100)")
    private BigDecimal variancePercentage;
}