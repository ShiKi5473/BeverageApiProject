package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

@Component("PREPARING") // 使用 Enum 名稱作為 Spring Bean 名稱
public class PreparingState extends AbstractOrderState {

    private final MemberPointService memberPointService;
    private final ApplicationEventPublisher eventPublisher;

    public PreparingState(MemberPointService memberPointService,
                          ApplicationEventPublisher eventPublisher) {
        this.memberPointService = memberPointService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected OrderStatus getStatus() { return OrderStatus.PREPARING; }

    /**
     * PREPARING 狀態「支援」完成
     */
    @Override
    public void complete(Order order) {
        // 【注意】此 "complete" 動作現在由 KDS 觸發，代表「製作完成」

        OrderStatus oldStatus = order.getStatus();

        // 【修改】狀態轉換
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        // 【修改】發布新事件
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.READY_FOR_PICKUP));
    }

    /**
     * PREPARING 狀態「支援」取消
     */
    @Override
    public void cancel(Order order) {
        OrderStatus oldStatus = order.getStatus();
        if (order.getMember() != null && order.getPointsUsed() > 0) {
            memberPointService.refundPoints(order.getMember(), order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.CANCELLED));
    }
}