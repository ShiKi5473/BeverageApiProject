package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.Date;

@Component("PREPARING") // 使用 Enum 名稱作為 Spring Bean 名稱
public class PreparingState extends AbstractOrderState {

    private final MemberPointService memberPointService;

    public PreparingState(MemberPointService memberPointService) {
        this.memberPointService = memberPointService;
    }

    @Override
    protected OrderStatus getStatus() { return OrderStatus.PREPARING; }

    /**
     * PREPARING 狀態「支援」完成
     */
    @Override
    public void complete(Order order) {
        // --- 這是從 OrderService.updateOrderStatus 搬移過來的邏輯 ---
        order.setCompletedTime(new Date());

        if (order.getMember() != null) {
            Long pointsEarned = memberPointService.calculatePointsEarned(order.getFinalAmount());
            order.setPointsEarned(pointsEarned);
            if (pointsEarned > 0) {
                memberPointService.earnPoints(order.getMember(), order, pointsEarned);
            }
        }
        // --- 邏輯結束 ---

        // 【關鍵】狀態轉換
        order.setStatus(OrderStatus.COMPLETED);
    }

    /**
     * PREPARING 狀態「支援」取消
     */
    @Override
    public void cancel(Order order) {
        // --- 這是從 OrderService.updateOrderStatus 搬移過來的邏輯 ---
        if (order.getMember() != null && order.getPointsUsed() > 0) {
            memberPointService.refundPoints(order.getMember(), order);
        }
        // --- 邏輯結束 ---

        // 【關鍵】狀態轉換
        order.setStatus(OrderStatus.CANCELLED);
    }
}