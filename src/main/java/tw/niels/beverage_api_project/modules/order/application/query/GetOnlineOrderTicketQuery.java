package tw.niels.beverage_api_project.modules.order.application.query;

/**
 * 查詢線上訂單 ticket 狀態（新增）。
 *
 * @param brandId   查詢者所屬品牌
 * @param requester 查詢者 principalKey（只能查詢自己送出的 ticket）
 * @param ticketId  ticket ID
 */
public record GetOnlineOrderTicketQuery(
    Long brandId,
    String requester,
    String ticketId
) {}
