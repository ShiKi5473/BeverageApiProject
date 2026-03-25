package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 更新訂單狀態命令
 */
public record UpdateOrderStatusCommand(
    Long brandId,
    Long orderId,
    OrderStatus status
) {}
