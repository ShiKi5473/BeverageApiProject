package tw.niels.beverage_api_project.modules.inventory.application.query;

/**
 * 查詢盤點清單的查詢
 */
public record GetInventoryAuditListQuery(
    Long brandId,
    Long storeId
) {}
