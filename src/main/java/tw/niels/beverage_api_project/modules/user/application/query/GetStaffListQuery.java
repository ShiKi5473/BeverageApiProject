package tw.niels.beverage_api_project.modules.user.application.query;

/**
 * 查詢員工列表的查詢
 */
public record GetStaffListQuery(
    Long brandId,
    Long storeId
) {}
