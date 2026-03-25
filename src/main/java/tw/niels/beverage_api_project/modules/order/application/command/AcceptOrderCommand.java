package tw.niels.beverage_api_project.modules.order.application.command;

/**
 * 接受訂單命令 (線上訂單 AWAITING_ACCEPTANCE -> PREPARING)
 */
public record AcceptOrderCommand(
    Long brandId,
    Long orderId
) {}
