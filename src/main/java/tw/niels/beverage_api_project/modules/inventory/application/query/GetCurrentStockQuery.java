package tw.niels.beverage_api_project.modules.inventory.application.query;

/**
 * 查詢當前庫存量的查詢
 */
public record GetCurrentStockQuery(
    Long storeId,
    Long itemId
) {}
