package tw.niels.beverage_api_project.modules.order.domain.event;

public record OrderPointsDeductedEvent(
    Long orderId,
    Long brandId,
    Long memberId,
    Long pointsUsed,
    String orderNumber
) {
    public static OrderPointsDeductedEvent of(Long orderId, Long brandId, Long memberId, Long pointsUsed, String orderNumber) {
        return new OrderPointsDeductedEvent(orderId, brandId, memberId, pointsUsed, orderNumber);
    }
}
