package tw.niels.beverage_api_project.modules.inventory.application.command;

import java.math.BigDecimal;

/**
 * 扣減庫存的命令 (手動或訂單觸發)
 */
public record DeductInventoryCommand(
    Long brandId,
    Long storeId,
    Long itemId,
    BigDecimal quantity
) {}
