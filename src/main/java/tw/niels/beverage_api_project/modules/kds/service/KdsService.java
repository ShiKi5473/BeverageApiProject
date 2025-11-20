package tw.niels.beverage_api_project.modules.kds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.kds.strategy.KdsEventStrategy;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class KdsService {

    private static final Logger logger = LoggerFactory.getLogger(KdsService.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // SSE 連線管理
    private final Map<Long, List<SseEmitter>> storeEmitters = new ConcurrentHashMap<>();
    private final Map<OrderStatus, KdsEventStrategy> strategyMap = new EnumMap<>(OrderStatus.class);

    public KdsService(List<KdsEventStrategy> strategies,
                      StringRedisTemplate redisTemplate,
                      ObjectMapper objectMapper) {
        for (KdsEventStrategy strategy : strategies) {
            strategyMap.put(strategy.getHandledStatus(), strategy);
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 觀察者模式：監聽訂單狀態變更事件
     */
    // 「發送 Redis 訊息」
    @EventListener
    public void handleOrderStateChange(OrderStateChangedEvent event) {
        Order order = event.getOrder();
        OrderStatus newStatus = event.getNewStatus();
        Long storeId = order.getStore().getStoreId();

        KdsEventStrategy strategy = strategyMap.get(newStatus);
        if (strategy != null) {
            KdsMessage message = strategy.createMessage(order);
            // 封裝 StoreId 以便接收端過濾
            KdsBroadcastMessage broadcastMsg = new KdsBroadcastMessage(storeId, message);

            try {
                String json = objectMapper.writeValueAsString(broadcastMsg);
                // 發布到 Redis
                redisTemplate.convertAndSend("kds-events", json);
            } catch (JsonProcessingException e) {
                logger.error("JSON 序列化失敗", e);
            }
        }
    }

    // 處理來自 Redis 的訊息 (所有 Server 實例都會收到)
    public void handleRedisMessage(String messageJson) {
        try {
            KdsBroadcastMessage msg = objectMapper.readValue(messageJson, KdsBroadcastMessage.class);
            // 檢查這台 Server 有沒有連著該店家的 Emitter
            sendToStore(msg.getStoreId(), msg.getKdsMessage());
        } catch (Exception e) {
            logger.error("處理 Redis 訊息失敗", e);
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
            // 使用 CopyOnWriteArrayList 的特性，避免 ConcurrentModificationException
            for (SseEmitter emitter : emitters) {
                try {
                    // 發送物件，Spring 會自動轉為 JSON
                    emitter.send(message);
                } catch (IOException e) {
                    // 發送失敗通常代表連線已斷，移除該 emitter
                    emitter.completeWithError(e);
                    removeEmitter(storeId, emitter);
                }
            }
        }
    }

    // 內部類別用於封裝 SSE 訊息格式
    // 建議加上 Getter 確保 Jackson 能序列化成 JSON
    public static class KdsMessage {
        private String action;
        private KdsOrderDto payload;

        public KdsMessage(){}

        public KdsMessage(String action, KdsOrderDto payload) {
            this.action = action;
            this.payload = payload;
        }

        public String getAction() { return action; }
        public KdsOrderDto getPayload() { return payload; }
    }

    public static class KdsBroadcastMessage {
        private Long storeId;
        private KdsMessage kdsMessage;

        public KdsBroadcastMessage() {}

        public KdsBroadcastMessage(Long storeId, KdsMessage kdsMessage) {
            this.storeId = storeId;
            this.kdsMessage = kdsMessage;
        }
        public Long getStoreId() { return storeId; }
        public KdsMessage getKdsMessage() { return kdsMessage; }

        public void setStoreId(Long storeId) {
            this.storeId = storeId;
        }
        public void setKdsMessage(KdsMessage kdsMessage) {
            this.kdsMessage = kdsMessage;
        }
    }
}