package tw.niels.beverage_api_project.modules.product.application.query;

/**
 * 查詢 POS 端完整商品資料的查詢
 */
public record GetProductForPosQuery(
    Long brandId
) {}
