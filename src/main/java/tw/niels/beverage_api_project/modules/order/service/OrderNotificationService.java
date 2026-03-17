package tw.niels.beverage_api_project.modules.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tw.niels.beverage_api_project.modules.order.dto.OrderNotificationDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.user.entity.User;

/**
 * 訂單通知服務：監聽訂單狀態變更事件，並透過各個管道（當前為 WebSocket）發送通知。
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(OrderNotificationService.class);
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 監聽訂單狀態變更事件
     * 使用 TransactionalEventListener 確保在事務成功提交 (AFTER_COMMIT) 後才發送通知
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStateChanged(OrderStateChangedEvent event) {
        Order order = event.order();
        User member = order.getMember();

        // 如果沒有關聯會員，則是 POS 直接點餐或訪客模式中未正確關聯 User 的情況
        // 訪客模式的 User 其 username 為 "GUEST:UUID"
        if (member == null) {
            logger.debug("訂單 {} 無關聯會員，跳過點對點通知", order.getOrderNumber());
            return;
        }

        String username = member.getPrimaryPhone();
        if (username == null) {
            logger.warn("訂單 {} 的會員 {} 無手機/使用者帳號，無法發送通知", order.getOrderNumber(), member.getId());
            return;
        }

        logger.info("發送訂單狀態通知給使用者 {}: {} -> {}", username, order.getOrderNumber(), event.newStatus());

        OrderNotificationDto notification = new OrderNotificationDto(
                order.getOrderNumber(),
                event.newStatus().name(),
                formatMessage(order.getOrderNumber(), event.newStatus())
        );

        // 推送到 STOMP 私人頻道：/user/{username}/queue/orders
        messagingTemplate.convertAndSendToUser(username, "/queue/orders", notification);
    }

    private String formatMessage(String orderNumber, OrderStatus status) {
        return switch (status) {
            case PREPARING -> String.format("您的訂單 %s 正在製作中", orderNumber);
            case READY_FOR_PICKUP -> String.format("您的訂單 %s 已製作完成，請至櫃檯取餐！", orderNumber);
            case CLOSED -> String.format("您的訂單 %s 已結案，感謝您的光臨", orderNumber);
            case CANCELLED -> String.format("您的訂單 %s 已取消", orderNumber);
            default -> String.format("您的訂單 %s 狀態已更新為 %s", orderNumber, status.name());
        };
    }
}
