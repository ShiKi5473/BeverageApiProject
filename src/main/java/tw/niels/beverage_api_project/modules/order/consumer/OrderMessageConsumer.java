package tw.niels.beverage_api_project.modules.order.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;

import java.io.IOException;

@Component
public class OrderMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final OrderProcessFacade orderProcessFacade;

    public OrderMessageConsumer(OrderProcessFacade orderProcessFacade) {
        this.orderProcessFacade = orderProcessFacade;
    }

    /**
     * 監聽線上訂單佇列
     * 使用手動 Ack 模式 (在 application.properties 中配置 listener.simple.acknowledge-mode=manual 或 auto)
     * 這裡假設使用 Spring Boot 預設 (Auto Ack on success, Nack on exception)
     * 但為了更細緻控制，我們可以在程式碼中處理例外。
     */
    @RabbitListener(queues = RabbitConfig.ONLINE_ORDER_QUEUE)
    public void handleOrderMessage(AsyncOrderTaskDto task,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        logger.info("【Consumer】收到訂單任務，RequestId: {}", task.getRequestId());

        try {
            // 呼叫 Facade 執行實際的資料庫寫入交易
            Order createdOrder = orderProcessFacade.createOrder(
                    task.getBrandId(),
                    task.getStoreId(),
                    task.getUserId(),
                    task.getOrderRequest()
            );

            logger.info("【Consumer】訂單建立成功: {} (RequestId: {})",
                    createdOrder.getOrderNumber(), task.getRequestId());

            // TODO: 在此處可以發送 WebSocket 通知前端「訂單已成立」

        } catch (Exception e) {
            logger.error("【Consumer】訂單處理失敗 (RequestId: {}): {}", task.getRequestId(), e.getMessage());

            // 錯誤處理策略：
            // 如果是業務邏輯錯誤 (如庫存不足 BadRequestException)，我們不應該重試，應該直接丟棄或進入死信
            // 如果是系統錯誤 (如 DB 連線失敗)，Spring 預設會重試

            // 這裡我們簡單地將例外拋出，讓 Spring AMQP 的 Retry 機制或 Dead Letter 機制接手
            throw e;
        }
    }
}