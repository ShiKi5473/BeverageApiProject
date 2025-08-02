package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

public interface ProductPosDto {
    Long getProductId();

    String getName();

    BigDecimal getBasePrice();
}
