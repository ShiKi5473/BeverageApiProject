package tw.niels.beverage_api_project.modules.order.consumer;

import com.rabbitmq.client.Channel;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.io.IOException;
import java.io.Serializable;

@Component
public class OrderMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final OrderProcessFacade orderProcessFacade;
    private final SimpMessagingTemplate messagingTemplate; // 注入 WebSocket 發送工具
    private final UserRepository userRepository; // 用於查詢使用者的 username (手機號)

    public OrderMessageConsumer(OrderProcessFacade orderProcessFacade,
                                SimpMessagingTemplate messagingTemplate,
                                UserRepository userRepository) {
        this.orderProcessFacade = orderProcessFacade;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = RabbitConfig.ONLINE_ORDER_QUEUE)
    public void handleOrderMessage(AsyncOrderTaskDto task,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        logger.info("【Consumer】收到訂單任務，RequestId: {}", task.getRequestId());

        try {
            // 1. 執行核心業務：寫入訂單
            Order createdOrder = orderProcessFacade.createOrder(
                    task.getBrandId(),
                    task.getStoreId(),
                    task.getUserId(),
                    task.getOrderRequest()
            );

            logger.info("【Consumer】訂單建立成功: {} (RequestId: {})",
                    createdOrder.getOrderNumber(), task.getRequestId());

            // 2. 發送 WebSocket 通知
            // 由於 convertAndSendToUser 預設使用 Principal.getName() (即 username/phone) 作為目標
            // 我們需要確保發送給正確的 "username"
            // AsyncOrderTaskDto 只有 userId，我們需要查出 username (phone)
            // (為了效能，其實也可以讓 DTO 直接帶入 username，但這裡先查 DB 確保正確性)
            userRepository.findByBrand_IdAndId(task.getBrandId(), task.getUserId())
                    .ifPresent(user -> {
                        String username = user.getPrimaryPhone();

                OrderNotification notification = new OrderNotification(
                        "ORDER_CREATED",
                        task.getRequestId(),
                        createdOrder.getId(),
                        createdOrder.getOrderNumber(),
                        "訂單建立成功！"
                );

                // 推送到: /user/{username}/queue/orders
                messagingTemplate.convertAndSendToUser(
                        username,
                        "/queue/orders",
                        notification
                );

                logger.debug("已發送 WebSocket 通知給使用者: {}", username);
            });

        } catch (Exception e) {
            logger.error("【Consumer】訂單處理失敗 (RequestId: {}): {}", task.getRequestId(), e.getMessage());
            // 這裡可以選擇發送 "失敗通知" 給前端
            // 為了簡化，暫時只記錄 Log 並拋出例外讓 MQ 處理重試/死信
            throw e;
        }
    }

    // 內部類別：通知訊息 DTO
    @Data
    public static class OrderNotification implements Serializable {
        private String type; // e.g., ORDER_CREATED, ORDER_FAILED
        private String ticketId;
        private Long orderId;
        private String orderNumber;
        private String message;

        public OrderNotification(String type, String ticketId, Long orderId, String orderNumber, String message) {
            this.type = type;
            this.ticketId = ticketId;
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.message = message;
        }
    }
}