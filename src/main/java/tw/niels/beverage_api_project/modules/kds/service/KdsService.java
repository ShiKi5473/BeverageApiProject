package tw.niels.beverage_api_project.modules.kds.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.kds.strategy.KdsEventStrategy;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

import java.io.IOException;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class KdsService {

    private static final Logger logger = LoggerFactory.getLogger(KdsService.class);

    // 改用 RabbitTemplate
    private final RabbitTemplate rabbitTemplate;

    // SSE 連線管理
    private final Map<Long, List<SseEmitter>> storeEmitters = new ConcurrentHashMap<>();
    private final Map<OrderStatus, KdsEventStrategy> strategyMap = new EnumMap<>(OrderStatus.class);

    public KdsService(List<KdsEventStrategy> strategies,
                      RabbitTemplate rabbitTemplate) {
        for (KdsEventStrategy strategy : strategies) {
            strategyMap.put(strategy.getHandledStatus(), strategy);
        }
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 觀察者模式：監聽訂單狀態變更事件 (Local Event)
     * 動作：「發送 RabbitMQ 訊息」到 Fanout Exchange
     */
    @EventListener
    public void handleOrderStateChange(OrderStateChangedEvent event) {
        Order order = event.order();
        OrderStatus newStatus = event.newStatus();
        Long storeId = order.getStore().getId();

        KdsEventStrategy strategy = strategyMap.get(newStatus);
        if (strategy != null) {
            KdsMessage message = strategy.createMessage(order);
            // 封裝 StoreId 以便接收端過濾
            KdsBroadcastMessage broadcastMsg = new KdsBroadcastMessage(storeId, message);

            try {
                // 發布到 RabbitMQ Fanout Exchange (Routing Key 為空字串)
                // 自動轉為 JSON
                rabbitTemplate.convertAndSend(RabbitConfig.KDS_EXCHANGE, "", broadcastMsg);
            } catch (Exception e) {
                logger.error("RabbitMQ 發送失敗", e);
            }
        }
    }

    /**
     * 處理來自 RabbitMQ 的訊息
     * 監聽與此實例綁定的匿名佇列 (SpEL 表達式參考 RabbitConfig 中的 Bean 名稱)
     */
    @RabbitListener(queues = "#{kdsAnonymousQueue.name}")
    public void handleRabbitMessage(KdsBroadcastMessage msg) {
        try {
            // 檢查這台 Server 有沒有連著該店家的 Emitter
            sendToStore(msg.getStoreId(), msg.getKdsMessage());
        } catch (Exception e) {
            logger.error("處理 RabbitMQ 訊息失敗", e);
        }
    }

    /**
     * 註冊新的 SSE 連線 (由 Controller 呼叫)
     */
    public void addEmitter(Long storeId, SseEmitter emitter) {
        storeEmitters.computeIfAbsent(storeId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        logger.info("店家 {} 新增 SSE 連線。目前連線數: {}", storeId, storeEmitters.get(storeId).size());

        // 設定移除回調
        emitter.onCompletion(() -> removeEmitter(storeId, emitter));
        emitter.onTimeout(() -> removeEmitter(storeId, emitter));
        emitter.onError((e) -> removeEmitter(storeId, emitter));
    }

    private void removeEmitter(Long storeId, SseEmitter emitter) {
        List<SseEmitter> emitters = storeEmitters.get(storeId);
        if (emitters != null) {
            emitters.remove(emitter);
            logger.debug("店家 {} 移除 SSE 連線", storeId);
        }
    }

    /**
     * 實際發送 SSE 訊息的方法
     */
    private void sendToStore(Long storeId, KdsMessage message) {
        List<SseEmitter> emitters = storeEmitters.get(storeId);
        if (emitters != null && !emitters.isEmpty()) {
            for (SseEmitter emitter : emitters) {
                try {
                    // 發送物件，Spring 會自動轉為 JSON
                    emitter.send(message);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(storeId, emitter);
                }
            }
        }
    }

    // DTO 類別 (需實作 Serializable 以便安全傳輸，雖然 JSON Converter 主要看 Getter)
    @Getter
    @Setter
    public static class KdsMessage implements Serializable {
        private String action;
        private KdsOrderDto payload;

        public KdsMessage(){}

        public KdsMessage(String action, KdsOrderDto payload) {
            this.action = action;
            this.payload = payload;
        }
    }

    @Getter
    @Setter
    public static class KdsBroadcastMessage implements Serializable {
        private Long storeId;
        private KdsMessage kdsMessage;

        public KdsBroadcastMessage() {}

        public KdsBroadcastMessage(Long storeId, KdsMessage kdsMessage) {
            this.storeId = storeId;
            this.kdsMessage = kdsMessage;
        }

    }
}