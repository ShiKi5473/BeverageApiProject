package tw.niels.beverage_api_project.modules.order.dto;

/**
 * 訂單通知 DTO (用於 WebSocket 推送)
 */
public record OrderNotificationDto(
    String orderNumber,
    String status,
    String message
) {}
