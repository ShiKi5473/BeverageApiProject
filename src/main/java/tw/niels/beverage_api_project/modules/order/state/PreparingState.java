package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

import java.util.Date;

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
        OrderStatus oldStatus = order.getStatus();
        order.setCompletedTime(new Date());

        if (order.getMember() != null) {
            Long pointsEarned = memberPointService.calculatePointsEarned(order.getFinalAmount());
            order.setPointsEarned(pointsEarned);
            if (pointsEarned > 0) {
                memberPointService.earnPoints(order.getMember(), order, pointsEarned);
            }
        }

        order.setStatus(OrderStatus.COMPLETED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.COMPLETED));
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