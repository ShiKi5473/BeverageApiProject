package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;

import java.util.List;

/**
 * POS 快速結帳命令 (一步到位)
 */
public record PosCheckoutCommand(
    Long brandId,
    Long storeId,
    Long userId,
    List<OrderItemDto> items,
    Long memberId,
    Long pointsToUse,
    String paymentMethod
) {}
