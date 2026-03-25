package tw.niels.beverage_api_project.modules.order.application.query;

import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 取得訂單列表查詢
 */
public record GetOrdersQuery(
    Long brandId,
    Long storeId,
    OrderStatus status
) {}
