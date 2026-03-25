package tw.niels.beverage_api_project.modules.product.application.query;

/**
 * 查詢品牌下所有商品摘要的查詢
 */
public record GetProductSummariesQuery(
    Long brandId
) {}
