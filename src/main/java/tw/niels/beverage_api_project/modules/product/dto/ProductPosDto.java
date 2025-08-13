package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPosDto {
    private Long id;

    private Long name;

    private BigDecimal basePrice;
}
