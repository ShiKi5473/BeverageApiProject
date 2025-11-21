package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "訂單金額試算結果")
public class OrderTotalDto {
    @Schema(description = "計算後的總金額", example = "150.00")
    private BigDecimal totalAmount;
}
