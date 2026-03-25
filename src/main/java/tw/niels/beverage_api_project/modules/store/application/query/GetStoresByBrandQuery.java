package tw.niels.beverage_api_project.modules.store.application.query;

/**
 * 查詢品牌下所有分店
 *
 * 由 StoreController 建構，傳遞至 StoreQueryHandler 處理。
 */
public record GetStoresByBrandQuery(
    Long brandId
) {}
