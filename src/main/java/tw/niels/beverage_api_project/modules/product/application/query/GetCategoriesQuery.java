package tw.niels.beverage_api_project.modules.product.application.query;

/**
 * 查詢品牌下所有分類的查詢
 */
public record GetCategoriesQuery(
    Long brandId
) {}
