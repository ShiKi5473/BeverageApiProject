package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

import java.util.Date;

@Component("READY_FOR_PICKUP")
public class ReadyForPickupState extends AbstractOrderState {

    private final MemberPointService memberPointService;
    private final ApplicationEventPublisher eventPublisher;

    public ReadyForPickupState(MemberPointService memberPointService,
                               ApplicationEventPublisher eventPublisher) {
        this.memberPointService = memberPointService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected OrderStatus getStatus() {
        return OrderStatus.READY_FOR_PICKUP;
    }

    @Override
    public void complete(Order order) {
        // 【注意】此 "complete" 動作由 POS 觸發，代表「顧客取餐」

        OrderStatus oldStatus = order.getStatus();

        // 【移入】這是從 PreparingState 搬過來的邏輯
        order.setCompletedTime(new Date()); // 設定訂單的最終完成時間
        if (order.getMember() != null) {
            Long pointsEarned = memberPointService.calculatePointsEarned(order.getFinalAmount());
            order.setPointsEarned(pointsEarned);
            if (pointsEarned > 0) {
                memberPointService.earnPoints(order.getMember(), order, pointsEarned);
            }
        }
        // --- 邏輯結束 ---

        order.setStatus(OrderStatus.CLOSED); // 【修改】狀態轉換至最終態

        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.CLOSED));
    }

    @Override
    public void cancel(Order order) {
        // (待取餐狀態也可以被取消，例如顧客久未取餐)
        OrderStatus oldStatus = order.getStatus();

        // 同樣需要退還點數
        if (order.getMember() != null && order.getPointsUsed() > 0) {
            memberPointService.refundPoints(order.getMember(), order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.CANCELLED));
    }
    @Override
    public void accept(Order order) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法執行接單動作。");
    }
}