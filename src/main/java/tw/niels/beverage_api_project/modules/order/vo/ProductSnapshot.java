package tw.niels.beverage_api_project.modules.order.vo;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long productId,
        String name,
        BigDecimal basePrice,
        String categoryName,
        String variantName
) {}