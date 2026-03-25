package tw.niels.beverage_api_project.modules.order.application.command;

/**
 * 處理訂單付款命令
 */
public record ProcessPaymentCommand(
    Long brandId,
    Long orderId,
    Long memberId,
    Long pointsToUse,
    String paymentMethod
) {}
