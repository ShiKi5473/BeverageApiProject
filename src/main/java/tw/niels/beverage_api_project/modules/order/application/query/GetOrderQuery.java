package tw.niels.beverage_api_project.modules.order.application.query;

/**
 * 取得單一訂單查詢
 */
public record GetOrderQuery(
    Long brandId,
    Long orderId
) {}
