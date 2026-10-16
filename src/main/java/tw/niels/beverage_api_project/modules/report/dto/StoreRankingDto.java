package tw.niels.beverage_api_project.modules.report.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "分店營收排行資料")
public class StoreRankingDto {

    // [P0-10] 回應中的 ID 序列化為字串，避免 JS 精度遺失
    @Schema(description = "分店 ID (字串)", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storeId;

    @Schema(description = "分店名稱", example = "台北信義店")
    private String storeName;

    @Schema(description = "總實收金額", example = "120000.00")
    private BigDecimal totalRevenue;

    public StoreRankingDto(Long storeId, BigDecimal totalRevenue) {
        this.storeId = storeId;
        this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }
}