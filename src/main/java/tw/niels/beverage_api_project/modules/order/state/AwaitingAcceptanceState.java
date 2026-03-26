package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

@Component("AWAITING_ACCEPTANCE") // Bean 名稱對應 Enum
public class AwaitingAcceptanceState extends AbstractOrderState {

    private final ApplicationEventPublisher eventPublisher;
    private final MemberPointService memberPointService;

    public AwaitingAcceptanceState(ApplicationEventPublisher eventPublisher, MemberPointService memberPointService) {
        this.eventPublisher = eventPublisher;
        this.memberPointService = memberPointService;
    }

    @Override
    protected OrderStatus getStatus() { return OrderStatus.AWAITING_ACCEPTANCE; }

    /**
     * 支援「接單」動作
     */
    @Override
    public void accept(OrderEntity OrderEntity) {
        OrderStatus oldStatus = OrderEntity.getStatus();

        // 狀態轉移： AWAITING_ACCEPTANCE -> PREPARING
        OrderEntity.setStatus(OrderStatus.PREPARING);

        // 【關鍵】發布事件
        // KdsService 會監聽到 PREPARING 狀態
        // 並推送 "NEW_ORDER" 訊息給 KDS 廚房
        eventPublisher.publishEvent(new OrderStateChangedEvent(OrderEntity, oldStatus, OrderStatus.PREPARING));
    }

    /**
     * 支援「取消」動作
     * (例如店家拒絕接單，或顧客取消)
     */
    @Override
    public void cancel(OrderEntity OrderEntity) {
        OrderStatus oldStatus = OrderEntity.getStatus();

        // TODO: 在這裡要加上「退款」邏輯 (例如呼叫金流 API)

        // 退還點數 (如果有點數折抵)
        if (OrderEntity.getMember() != null && OrderEntity.getPointsUsed() > 0) {
            memberPointService.refundPoints(OrderEntity.getMember(), OrderEntity);
        }

        OrderEntity.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(OrderEntity, oldStatus, OrderStatus.CANCELLED));
    }
}