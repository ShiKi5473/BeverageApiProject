package tw.niels.beverage_api_project.modules.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "分店營收排行資料")
public class StoreRankingDto {

    @Schema(description = "分店 ID", example = "1")
    private Long storeId;

    @Schema(description = "分店名稱", example = "台北信義店")
    private String storeName;

    @Schema(description = "總實收金額", example = "120000.00")
    private BigDecimal totalRevenue;
}