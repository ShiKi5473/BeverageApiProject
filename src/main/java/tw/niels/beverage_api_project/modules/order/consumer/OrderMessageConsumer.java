// 路徑：src/main/java/tw/niels/beverage_api_project/modules/order/consumer/OrderMessageConsumer.java
package tw.niels.beverage_api_project.modules.order.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;

@Component
public class OrderMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final OrderProcessFacade orderProcessFacade;
    private final ApplicationEventPublisher eventPublisher;

    public OrderMessageConsumer(OrderProcessFacade orderProcessFacade,
                                ApplicationEventPublisher eventPublisher) {
        this.orderProcessFacade = orderProcessFacade;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = RabbitConfig.ONLINE_ORDER_QUEUE)
    public void handleOrderMessage(AsyncOrderTaskDto task,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

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

            // 2. 發布領域事件 (解耦通知機制)
            // 讓 KdsService 監聽此事件並透過 SSE 推播
            // oldStatus 為 null 代表這是新建立的訂單
            eventPublisher.publishEvent(new OrderStateChangedEvent(
                    createdOrder,
                    null,
                    createdOrder.getStatus()
            ));

        } catch (Exception e) {
            logger.error("【Consumer】訂單處理失敗 (RequestId: {}): {}", task.getRequestId(), e.getMessage());
            // TODO 實際專案中建議將失敗訊息轉發至死信佇列 (DLQ) 或發送失敗通知
            throw e;
        }
    }
}