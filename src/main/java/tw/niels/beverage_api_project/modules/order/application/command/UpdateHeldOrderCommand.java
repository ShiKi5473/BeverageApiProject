package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;

/**
 * 更新暫存/待處理訂單命令
 */
public record UpdateHeldOrderCommand(
    Long brandId,
    Long orderId,
    OrderStatus status,
    List<OrderItemDto> items
) {}
