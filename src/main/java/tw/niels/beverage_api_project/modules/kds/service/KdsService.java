package tw.niels.beverage_api_project.modules.kds.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.niels.beverage_api_project.config.AsyncConfig;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderCancelledEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderPlacedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderStatusChangedEvent;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

/**
 * KDS（廚房顯示系統）即時推播服務。
 * <p>
 * 【P0-4 重寫】原本只監聽舊的 {@code OrderStateChangedEvent}（payload 為 JPA 實體），
 * 但 DDD Handler 只發布新的 domain event，導致 POS 訂單與所有狀態變更都不會推播到 KDS。
 * 現在改為監聽 {@code modules/order/domain/event} 下的四種 domain event：
 * <ul>
 *   <li>{@link OrderStatusChangedEvent}：依 newStatus 推播（PREPARING → NEW_ORDER、READY_FOR_PICKUP → MOVE_TO_PICKUP；
 *       PENDING／HELD 不推播）</li>
 *   <li>{@link OrderPlacedEvent}：只有載入後狀態為 AWAITING_ACCEPTANCE（線上訂單）才推播 PENDING_ACCEPTANCE</li>
 *   <li>{@link OrderClosedEvent}：REMOVE_FROM_PICKUP</li>
 *   <li>{@link OrderCancelledEvent}：CANCEL_ORDER</li>
 * </ul>
 * 監聽器在交易提交後（AFTER_COMMIT）於 {@code kdsExecutor} 非同步執行，
 * 並在「唯讀的新交易」中以 {@link OrderJpaRepository#findByIdForSystem(Long)} 載入訂單、組出 {@link KdsOrderDto}，
 * 避免在 @Async 執行緒中讀取游離實體的延遲集合（LazyInitializationException）。
 * </p>
 * <p>
 * 跨實例廣播流程維持不變：本機監聽器 → RabbitMQ Fanout（kds.exchange）→ 每個實例的匿名佇列 →
 * {@link #handleRabbitMessage(KdsBroadcastMessage)} → 推送給連在本實例上的 SSE 用戶端。
 * </p>
 */
@Service
public class KdsService {

    private static final Logger logger = LoggerFactory.getLogger(KdsService.class);

    // ===== 前端（kds.js / pos.js）依 action 決定卡片欄位，名稱需與 AGENT_CONTRACT 一致 =====
    /** 線上訂單待接單（KDS「待接單」欄） */
    public static final String ACTION_PENDING_ACCEPTANCE = "PENDING_ACCEPTANCE";
    /** 進入製作中（POS 結帳或接單後） */
    public static final String ACTION_NEW_ORDER = "NEW_ORDER";
    /** 製作完成，移到待取餐 */
    public static final String ACTION_MOVE_TO_PICKUP = "MOVE_TO_PICKUP";
    /** 顧客取餐完成，從待取餐移除 */
    public static final String ACTION_REMOVE_FROM_PICKUP = "REMOVE_FROM_PICKUP";
    /** 訂單取消 */
    public static final String ACTION_CANCEL_ORDER = "CANCEL_ORDER";

    /** SSE 連線逾時（30 分鐘）；逾時後前端 sseClient 會重新取得 ticket 重連 */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final RabbitTemplate rabbitTemplate;
    // SSE 連線生命週期管理委派給 SseEmitterManager，與業務邏輯分離
    private final SseEmitterManager sseEmitterManager;
    // 【新增】依 ID 載入訂單（系統內部用途，不需 brandId 隔離：事件本身來自可信的訂單 Handler）
    private final OrderJpaRepository orderJpaRepository;
    // 【新增】程式化的唯讀交易：避免 @Transactional 自我呼叫（self-invocation）不生效的問題
    private final TransactionTemplate readOnlyTransaction;

    public KdsService(RabbitTemplate rabbitTemplate,
                      SseEmitterManager sseEmitterManager,
                      OrderJpaRepository orderJpaRepository,
                      PlatformTransactionManager transactionManager) {
        this.rabbitTemplate = rabbitTemplate;
        this.sseEmitterManager = sseEmitterManager;
        this.orderJpaRepository = orderJpaRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        // 使用 REQUIRES_NEW：kdsExecutor 滿載時 CallerRunsPolicy 會在發布者執行緒（原交易的 AFTER_COMMIT 階段）執行，
        // 此時舊交易資源仍綁定在執行緒上，REQUIRED 會「加入」已提交的交易；REQUIRES_NEW 確保一定開新的交易與 EntityManager
        this.readOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 前端建立 SSE 連線時呼叫此方法
     *
     * @param storeId 訂閱的分店 ID（不可為 null，由 Controller 驗證）
     */
    public SseEmitter subscribe(Long storeId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseEmitterManager.addEmitter(storeId, emitter);
        try {
            // 【新增】連線建立後立即送出一行 SSE 註解，讓回應標頭立刻送出（反向代理不會緩衝住），
            // 前端 EventSource 能馬上觸發 onopen；EventSource 會忽略註解，不影響訊息處理
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            logger.debug("店家 {} 的 SSE 初始註解送出失敗，移除連線: {}", storeId, e.getMessage());
            sseEmitterManager.removeEmitter(storeId, emitter);
        }
        return emitter;
    }

    // ======================================================================
    // 【第一步】監聽本地 domain event → 載入訂單 → 廣播到 RabbitMQ
    // ======================================================================

    /**
     * 訂單狀態變更（POS 結帳 PENDING→PREPARING、接單 AWAITING_ACCEPTANCE→PREPARING、製作完成 PREPARING→READY_FOR_PICKUP）。
     * <p>
     * 只處理 PREPARING 與 READY_FOR_PICKUP：PENDING／HELD 不在廚房顯示；
     * AWAITING_ACCEPTANCE、CLOSED、CANCELLED 分別由 OrderPlacedEvent、OrderClosedEvent、OrderCancelledEvent 專責推播，
     * 這裡忽略以避免同一次狀態變更重複推播（即使未來 Aggregate 同時發布兩種事件也不會重複）。
     * </p>
     * fallbackExecution = true：若發布端沒有交易（資料已自動提交），仍立即處理，避免事件被靜默丟棄。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus newStatus = event.newStatus();
        if (newStatus != OrderStatus.PREPARING && newStatus != OrderStatus.READY_FOR_PICKUP) {
            logger.debug("訂單 {} 狀態變為 {}，KDS 不推播（或由專屬事件處理）", event.orderId(), newStatus);
            return;
        }
        pushOrder(event.orderId(), actionForStatus(newStatus), newStatus, null);
    }

    /**
     * 訂單建立：只有線上訂單（載入後狀態為 AWAITING_ACCEPTANCE）才推播「待接單」。
     * POS 結帳在同一交易內 place + processPayment，載入時已是 PREPARING → 由 OrderStatusChangedEvent 推播 NEW_ORDER；
     * 一般 POS 建單（PENDING／HELD）不顯示在廚房 → 忽略。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPlaced(OrderPlacedEvent event) {
        pushOrder(event.orderId(), ACTION_PENDING_ACCEPTANCE, OrderStatus.AWAITING_ACCEPTANCE,
                OrderStatus.AWAITING_ACCEPTANCE);
    }

    /**
     * 訂單完成（顧客取餐）：從待取餐區移除。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderClosed(OrderClosedEvent event) {
        pushOrder(event.orderId(), ACTION_REMOVE_FROM_PICKUP, OrderStatus.CLOSED, null);
    }

    /**
     * 訂單取消：從廚房／待取餐畫面移除。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        pushOrder(event.orderId(), ACTION_CANCEL_ORDER, OrderStatus.CANCELLED, null);
    }

    /**
     * 依 domain 狀態決定前端 action（對照 AGENT_CONTRACT「Realtime」）；PENDING／HELD 回傳 null（不推播）。
     */
    static String actionForStatus(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case AWAITING_ACCEPTANCE -> ACTION_PENDING_ACCEPTANCE;
            case PREPARING -> ACTION_NEW_ORDER;
            case READY_FOR_PICKUP -> ACTION_MOVE_TO_PICKUP;
            case CLOSED -> ACTION_REMOVE_FROM_PICKUP;
            case CANCELLED -> ACTION_CANCEL_ORDER;
            case PENDING, HELD -> null;
        };
    }

    /**
     * 載入訂單、組出 KDS 訊息並廣播到 RabbitMQ。
     *
     * @param orderId        訂單 ID
     * @param action         前端動作
     * @param eventStatus    事件所代表的狀態（寫入 payload.status，讓 status 與 action 一致；
     *                       非同步處理時訂單可能已進入下一個狀態）
     * @param requiredStatus 若不為 null，只有載入後的訂單狀態等於此值才推播（OrderPlacedEvent 用）
     */
    private void pushOrder(Long orderId, String action, OrderStatus eventStatus, OrderStatus requiredStatus) {
        if (orderId == null || action == null) {
            logger.warn("KDS 事件缺少 orderId 或 action，略過推播 (orderId={}, action={})", orderId, action);
            return;
        }
        try {
            Optional<LoadedOrder> loaded = loadOrder(orderId);
            if (loaded.isEmpty()) {
                logger.warn("KDS 推播時找不到訂單 {}（可能已被刪除或交易未提交）", orderId);
                return;
            }
            LoadedOrder order = loaded.get();
            // JPA 與 domain 各有一個 OrderStatus enum（常數名稱相同），以名稱比較
            if (requiredStatus != null && !requiredStatus.name().equals(order.status())) {
                logger.debug("訂單 {} 目前狀態為 {}，非 {}，不推播 {}", orderId, order.status(), requiredStatus, action);
                return;
            }
            if (order.storeId() == null) {
                logger.warn("訂單 {} 沒有分店，無法推播 KDS", orderId);
                return;
            }

            KdsOrderDto dto = order.dto();
            // payload.status 與 action 對應的狀態一致（例如 MOVE_TO_PICKUP → READY_FOR_PICKUP）
            dto.setStatus(eventStatus.name());

            KdsBroadcastMessage broadcastMsg = new KdsBroadcastMessage(order.storeId(), new KdsMessage(action, dto));
            // 發布到 RabbitMQ Fanout Exchange -> 所有 Server 實例都會收到，各自推給自己持有的 SSE 連線
            rabbitTemplate.convertAndSend(RabbitConfig.KDS_EXCHANGE, "", broadcastMsg);
            logger.debug("已廣播 KDS 訊息: {} (訂單: {}, 分店: {})", action, orderId, order.storeId());
        } catch (AmqpException e) {
            // KDS 廣播失敗不影響訂單（訂單已提交成功），只記錄錯誤
            logger.error("KDS RabbitMQ 廣播失敗 (訂單: {}, action: {})", orderId, action, e);
        } catch (RuntimeException e) {
            // 非同步監聽器的例外不會傳回呼叫端，這裡記錄後吞掉，避免只出現在 AsyncUncaughtExceptionHandler
            logger.error("KDS 推播處理失敗 (訂單: {}, action: {})", orderId, action, e);
        }
    }

    /**
     * 在唯讀新交易中載入訂單並組出 DTO（所有延遲集合都在交易內讀取完畢）。
     */
    private Optional<LoadedOrder> loadOrder(Long orderId) {
        LoadedOrder result = readOnlyTransaction.execute(status ->
                orderJpaRepository.findByIdForSystem(orderId)
                        .map(KdsService::toLoadedOrder)
                        .orElse(null));
        return Optional.ofNullable(result);
    }

    private static LoadedOrder toLoadedOrder(OrderEntity order) {
        Long storeId = order.getStore() != null ? order.getStore().getId() : null;
        String status = order.getStatus() != null ? order.getStatus().name() : null;
        return new LoadedOrder(storeId, status, KdsOrderDto.fromEntity(order));
    }

    /**
     * 交易內載入的結果（交易外只使用這些已轉換好的值）。
     */
    private record LoadedOrder(Long storeId, String status, KdsOrderDto dto) {
    }

    // ======================================================================
    // 【第二步】監聽 RabbitMQ 廣播 → 推送給本實例的 SSE 連線
    // ======================================================================

    /**
     * 每個 Server 實例都有自己的匿名 Queue 綁定到 KDS Exchange
     * 動作：檢查自己有沒有該店家的連線，有的話就推送 SSE
     */
    @RabbitListener(queues = "#{kdsAnonymousQueue.name}")
    public void handleRabbitMessage(KdsBroadcastMessage msg) {
        try {
            if (msg == null || msg.getKdsMessage() == null) {
                return;
            }
            sendToStore(msg.getStoreId(), msg.getKdsMessage());
        // 保留 catch(Exception)：消費端需防止未預期例外導致訊息重試／進入 DLQ（推播失敗不需重送）
        } catch (Exception e) {
            logger.error("處理 KDS RabbitMQ 訊息失敗", e);
        }
    }

    /**
     * 實際執行 SSE 推送。
     * <p>
     * 【修正】改由 {@link SseEmitterManager#broadcast(Long, Object)} 推送：對每個 emitter 個別捕捉
     * {@code IOException | IllegalStateException}（已完成的 emitter 會拋 IllegalStateException）並移除，
     * 原本只捕捉 IOException，一個失效連線就會中斷對同店其他螢幕的推播。
     * </p>
     */
    private void sendToStore(Long storeId, KdsMessage message) {
        if (storeId == null) {
            return;
        }
        // 封裝成前端需要的格式：data: {"action": ..., "payload": KdsOrderDto}
        SseEventPayload payload = new SseEventPayload(message.getAction(), message.getPayload());
        sseEmitterManager.broadcast(storeId, payload);
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

    // 用於 SSE 輸出的封裝 (根據 kds.js / pos.js 的解析邏輯：data.action、data.payload)
    private static class SseEventPayload {
        public String action;
        public Object payload;
        public SseEventPayload(String action, Object payload) {
            this.action = action;
            this.payload = payload;
        }
    }
}
