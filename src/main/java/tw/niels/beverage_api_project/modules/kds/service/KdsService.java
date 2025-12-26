package tw.niels.beverage_api_project.modules.kds.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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

    private final RabbitTemplate rabbitTemplate;

    // SSE 連線管理 Map<StoreId, List<Emitter>>
    private final Map<Long, List<SseEmitter>> storeEmitters = new ConcurrentHashMap<>();

    // 保留策略模式，用於處理特定狀態的複雜邏輯 (如果有的話)
    private final Map<OrderStatus, KdsEventStrategy> strategyMap = new EnumMap<>(OrderStatus.class);

    public KdsService(List<KdsEventStrategy> strategies,
                      RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        for (KdsEventStrategy strategy : strategies) {
            strategyMap.put(strategy.getHandledStatus(), strategy);
        }
    }

    /**
     * 前端建立 SSE 連線時呼叫此方法
     */
    public SseEmitter subscribe(Long storeId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分鐘
        addEmitter(storeId, emitter);
        return emitter;
    }

    public void addEmitter(Long storeId, SseEmitter emitter) {
        storeEmitters.computeIfAbsent(storeId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.debug("店家 {} 新增 SSE 連線。目前連線數: {}", storeId, storeEmitters.get(storeId).size());

        emitter.onCompletion(() -> removeEmitter(storeId, emitter));
        emitter.onTimeout(() -> removeEmitter(storeId, emitter));
        emitter.onError((e) -> removeEmitter(storeId, emitter));
    }

    private void removeEmitter(Long storeId, SseEmitter emitter) {
        List<SseEmitter> emitters = storeEmitters.get(storeId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    /**
     * 【第一步】監聽本地事件 (Local Event Listener)
     * 觸發來源：OrderMessageConsumer, OrderService 等
     * 動作：決定 Action 並「廣播」到 RabbitMQ
     * Update: 改用 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
     * 確保只有在 DB 事務提交成功後，才發送通知，避免 Race Condition。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStateChange(OrderStateChangedEvent event) {
        Order order = event.order();
        if (order.getStore() == null) return;

        Long storeId = order.getStore().getId();

        // 1. 轉換 DTO
        KdsOrderDto orderDto = KdsOrderDto.fromEntity(order);

        // 2. 決定前端動作 (Action)
        // 優先使用我們自定義的邏輯，涵蓋 NEW_ORDER
        String action = determineAction(event);

        // (可選) 如果有特定狀態的複雜策略，也可以在這裡合併使用 strategyMap
        // KdsEventStrategy strategy = strategyMap.get(event.newStatus());
        // if (strategy != null) { ... }

        // 3. 封裝訊息
        KdsMessage message = new KdsMessage(action, orderDto);
        KdsBroadcastMessage broadcastMsg = new KdsBroadcastMessage(storeId, message);

        try {
            // 4. 發布到 RabbitMQ Fanout Exchange -> 所有 Server 實例都會收到
            rabbitTemplate.convertAndSend(RabbitConfig.KDS_EXCHANGE, "", broadcastMsg);
            logger.debug("已廣播 RabbitMQ 訊息: {} (Store: {})", action, storeId);
        } catch (Exception e) {
            logger.error("RabbitMQ 發送失敗", e);
        }
    }

    /**
     * 【第二步】監聽 RabbitMQ 訊息 (Distributed Listener)
     * 每個 Server 實例都有自己的匿名 Queue 綁定到 KDS Exchange
     * 動作：檢查自己有沒有該店家的連線，有的話就推送 SSE
     */
    @RabbitListener(queues = "#{kdsAnonymousQueue.name}")
    public void handleRabbitMessage(KdsBroadcastMessage msg) {
        try {
            sendToStore(msg.getStoreId(), msg.getKdsMessage());
        } catch (Exception e) {
            logger.error("處理 RabbitMQ 訊息失敗", e);
        }
    }

    /**
     * 實際執行 SSE 推送
     */
    private void sendToStore(Long storeId, KdsMessage message) {
        List<SseEmitter> emitters = storeEmitters.get(storeId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        // 封裝成前端需要的格式
        SseEventPayload payload = new SseEventPayload(message.getAction(), message.getPayload());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (IOException e) {
                removeEmitter(storeId, emitter);
            }
        }
    }

    /**
     * 根據事件判斷前端 Action
     */
    private String determineAction(OrderStateChangedEvent event) {
        if (event.oldStatus() == null) {
            return "NEW_ORDER";
        }
        return switch (event.newStatus()) {
            case READY_FOR_PICKUP -> "MOVE_TO_PICKUP";
            case CLOSED -> "REMOVE_FROM_PICKUP";
            case CANCELLED -> "CANCEL_ORDER";
            default -> "UPDATE_ORDER";
        };
    }

    // --- DTOs ---

    @Getter
    @Setter
    public static class KdsMessage implements Serializable {
        private String action;
        private KdsOrderDto payload;
        public KdsMessage() {}
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

    // 用於 SSE 輸出的封裝 (根據 pos.js 的解析邏輯)
    private static class SseEventPayload {
        public String action;
        public Object payload;
        public SseEventPayload(String action, Object payload) {
            this.action = action;
            this.payload = payload;
        }
    }
}