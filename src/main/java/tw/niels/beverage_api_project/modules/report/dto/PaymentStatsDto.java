package tw.niels.beverage_api_project.modules.report.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "付款方式統計資料")
public class PaymentStatsDto {
    @Schema(description = "付款方式代碼", example = "CASH")
    private String paymentCode;

    @Schema(description = "總實收金額", example = "5000.00")
    private BigDecimal totalAmount;
}