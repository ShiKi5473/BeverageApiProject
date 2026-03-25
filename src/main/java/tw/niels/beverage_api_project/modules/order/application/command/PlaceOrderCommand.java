package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;

/**
 * 建立訂單命令 (線上或暫存)
 */
public record PlaceOrderCommand(
    Long brandId,
    Long storeId,
    Long userId,
    OrderStatus status,
    List<OrderItemDto> items
) {}
