package tw.niels.beverage_api_project.modules.brand.application.query;

/**
 * 根據 ID 查詢品牌的查詢
 */
public record GetBrandByIdQuery(
    Long brandId
) {}
