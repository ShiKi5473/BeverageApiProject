package tw.niels.beverage_api_project.modules.order.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.exception.AppException;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.PlaceOnlineOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;

import java.util.Optional;

/**
 * 線上訂單 RabbitMQ 消費者。
 *
 * 修改說明（B-4、P0-4）：
 * - 改呼叫 PlaceOnlineOrderHandler（取代已刪除的 OrderProcessFacade）：初始狀態固定 AWAITING_ACCEPTANCE，
 *   綁定 memberId 或 guestRef，以 ticketId 作為 request_id（冪等，重送不會重複建單）。
 * - 不再發佈舊的 OrderStateChangedEvent；領域事件（OrderPlacedEvent）由 Handler 在交易內發佈，
 *   KDS「待接單」與顧客通知在交易提交後（AFTER_COMMIT）才觸發。
 * - 處理結果寫回 Redis ticket（CREATED / FAILED），供 GET /online-orders/{ticketId} 查詢。
 *
 * 修改說明（m-1 重試分類與 ticket 最終狀態）：
 * - 原本每次嘗試失敗都把 ticket 標為 FAILED，而 listener retry 會重試所有例外（包含業務錯誤），
 *   ticket 可能先顯示 FAILED、重試成功後又變 CREATED（前端看到 FAILED 重新下單 → 重複訂單）。
 * - 現在改用 RabbitConfig.ONLINE_ORDER_LISTENER_FACTORY（分類重試 + 最終失敗處理）：
 *   - 業務 / 驗證錯誤（AppException、DomainException）包成 AmqpRejectAndDontRequeueException → 不重試
 *   - 暫時性錯誤原樣拋出 → 依設定重試；重試期間 ticket 維持 QUEUED
 *   - 只有最後一次失敗（或不可重試）才由 {@link #recover(Message, Throwable)} 把 ticket 標為 FAILED，
 *     之後訊息 reject 且不 requeue → DLQ
 * - 每次失敗仍先確認訂單是否其實已建立（同一 ticket 併發處理造成 request_id 唯一鍵衝突），已建立就視為成功。
 */
@Component
public class OrderMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMessageConsumer.class);

    /** 非業務錯誤（如資料庫暫時無法連線）時給前端的訊息，不外洩內部細節 */
    private static final String GENERIC_FAILURE_MESSAGE = "訂單建立失敗，請稍後再試";

    /** 新增：最終失敗處理時把訊息 body 轉回任務 DTO 的目標型別（不依賴 __TypeId__ header 與信任套件設定） */
    private static final ParameterizedTypeReference<AsyncOrderTaskDto> TASK_TYPE = new ParameterizedTypeReference<>() {
    };

    /** 新增：例外原因鏈最多追溯的層數（防止循環 cause 造成無窮迴圈） */
    private static final int MAX_CAUSE_DEPTH = 20;

    private final PlaceOnlineOrderHandler placeOnlineOrderHandler;
    private final OnlineOrderTicketService ticketService;
    // 新增：最終失敗處理（recover）只拿得到原始 AMQP 訊息，需要用同一個 JSON converter 轉回任務 DTO
    private final MessageConverter messageConverter;

    public OrderMessageConsumer(PlaceOnlineOrderHandler placeOnlineOrderHandler,
                                OnlineOrderTicketService ticketService,
                                MessageConverter messageConverter) {
        this.placeOnlineOrderHandler = placeOnlineOrderHandler;
        this.ticketService = ticketService;
        this.messageConverter = messageConverter;
    }

    /**
     * 處理一次線上訂單任務（每次重試都會再呼叫一次）。
     *
     * 修改（m-1）：指定 containerFactory = ONLINE_ORDER_LISTENER_FACTORY（分類重試 + 最終失敗處理）；
     * 失敗時不再直接把 ticket 標為 FAILED，改由重試用盡後的 {@link #recover(Message, Throwable)} 處理。
     */
    @RabbitListener(queues = RabbitConfig.ONLINE_ORDER_QUEUE,
            containerFactory = RabbitConfig.ONLINE_ORDER_LISTENER_FACTORY)
    public void handleOrderMessage(AsyncOrderTaskDto task) {
        String ticketId = task.getRequestId();
        logger.info("【Consumer】收到線上訂單任務，RequestId: {}", ticketId);

        try {
            // 1. 建立線上訂單（Handler 內含冪等檢查；交易提交後才回到這裡）
            PlaceOnlineOrderHandler.Result result = placeOnlineOrderHandler.handle(new PlaceOnlineOrderCommand(
                    ticketId,
                    task.getBrandId(),
                    task.getStoreId(),
                    task.getMemberId(),
                    task.getGuestRef(),
                    task.getItems(),
                    task.getCustomerNote()
            ));

            logger.info("【Consumer】線上訂單{}: {} (RequestId: {})",
                    result.created() ? "建立成功" : "已存在（重送略過）", result.orderNumber(), ticketId);

            // 2. 更新 ticket 為 CREATED（盡力而為：失敗時查詢端會以資料庫的 requestId 備援）
            markCreatedQuietly(task, result);

        } catch (RuntimeException e) {
            // 3. 先確認訂單是否其實已建立（例如同一 ticket 併發處理造成唯一鍵衝突）
            Optional<PlaceOnlineOrderHandler.Result> existing = findExistingQuietly(task);
            if (existing.isPresent()) {
                logger.warn("【Consumer】處理時發生例外但訂單已存在，視為成功 (RequestId: {}): {}",
                        ticketId, e.getMessage());
                markCreatedQuietly(task, existing.get());
                return;
            }

            // 4. 業務 / 驗證錯誤：重試也不會成功 → 包成 AmqpRejectAndDontRequeueException（RabbitConfig 判定不重試），
            //    直接進入 recover：ticket 標為 FAILED（附業務訊息）並轉入 DLQ
            if (isBusinessError(e)) {
                logger.warn("【Consumer】線上訂單業務驗證失敗，不重試 (RequestId: {}): {}", ticketId, e.getMessage());
                throw new AmqpRejectAndDontRequeueException("線上訂單業務驗證失敗，RequestId: " + ticketId, e);
            }

            // 5. 暫時性錯誤：原樣拋出交給重試機制；ticket 維持 QUEUED，最後一次仍失敗才由 recover 標為 FAILED
            logger.warn("【Consumer】線上訂單處理失敗，交由重試機制 (RequestId: {}): {}", ticketId, e.getMessage());
            throw e;
        }
    }

    /**
     * 最終失敗處理（新增，m-1）：重試用盡或遇到不可重試的錯誤時，由 RabbitConfig 的 MessageRecoverer 呼叫，
     * 每則訊息只會呼叫一次。
     *
     * 1. 由訊息 body 還原任務（還原失敗時改用 AMQP messageId，SubmitOnlineOrderHandler 會設為 ticketId）
     * 2. 若訂單其實已建立 → ticket 標為 CREATED，回傳 true（訊息 ack，不進 DLQ）
     * 3. 否則 ticket 標為 FAILED（業務錯誤回傳原訊息，其他錯誤使用通用訊息），回傳 false（訊息轉入 DLQ）
     *
     * @param message 原始 AMQP 訊息
     * @param cause   最後一次失敗的原因（通常是包著監聽者例外的 ListenerExecutionFailedException）
     * @return true 表示訂單其實已建立、訊息可以直接確認；false 表示確定失敗、訊息應轉入 DLQ
     */
    public boolean recover(Message message, Throwable cause) {
        AsyncOrderTaskDto task = extractTask(message);
        String ticketId = task != null ? task.getRequestId() : null;
        if ((ticketId == null || ticketId.isBlank()) && message != null) {
            ticketId = message.getMessageProperties().getMessageId();
        }
        if (ticketId == null || ticketId.isBlank()) {
            logger.error("【Consumer】線上訂單最終失敗，且無法辨識 ticket，訊息直接轉入 DLQ: {}", rootMessage(cause));
            return false;
        }

        // 最後再確認一次訂單是否已建立（例如最後一次嘗試其實已提交、只是回應階段失敗）
        if (task != null) {
            Optional<PlaceOnlineOrderHandler.Result> existing = findExistingQuietly(task);
            if (existing.isPresent()) {
                logger.warn("【Consumer】重試用盡但訂單已存在，標記為 CREATED (RequestId: {})", ticketId);
                markCreatedQuietly(task, existing.get());
                return true;
            }
        }

        logger.error("【Consumer】線上訂單處理最終失敗，轉入 DLQ (RequestId: {}): {}", ticketId, rootMessage(cause), cause);
        try {
            ticketService.markFailed(ticketId,
                    task != null ? task.getBrandId() : null,
                    task != null ? task.getRequester() : null,
                    toUserMessage(cause));
        } catch (RuntimeException redisError) {
            logger.warn("【Consumer】更新 ticket 為 FAILED 失敗 (RequestId: {}): {}", ticketId, redisError.getMessage());
        }
        return false;
    }

    // ─── 私有輔助方法 ─────────────────────────────────────────────────────

    /**
     * 新增：是否為業務 / 驗證錯誤（400/404/422），這類錯誤重試結果也不會改變。
     */
    static boolean isBusinessError(Throwable e) {
        return findBusinessError(e) != null;
    }

    /**
     * 業務錯誤（400/404/422）回傳原訊息給前端；其他錯誤使用通用訊息。
     * 修改（m-1）：沿著 cause 鏈尋找（recover 收到的是容器包裝過的例外）。
     */
    static String toUserMessage(Throwable e) {
        Throwable business = findBusinessError(e);
        if (business != null && business.getMessage() != null) {
            return business.getMessage();
        }
        return GENERIC_FAILURE_MESSAGE;
    }

    /** 新增：在 cause 鏈中尋找第一個業務例外（AppException / DomainException） */
    private static Throwable findBusinessError(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof AppException || current instanceof DomainException) {
                return current;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    /** 新增：取 cause 鏈最底層的訊息（日誌用） */
    private static String rootMessage(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current
                && depth++ < MAX_CAUSE_DEPTH) {
            current = current.getCause();
        }
        return current != null ? current.getMessage() : null;
    }

    /**
     * 新增：把原始訊息 body 轉回任務 DTO（明確指定型別，不依賴 __TypeId__ header）。
     * 毒訊息（JSON 格式錯誤）轉換失敗時回傳 null。
     */
    private AsyncOrderTaskDto extractTask(Message message) {
        if (message == null) {
            return null;
        }
        try {
            Object payload = messageConverter instanceof SmartMessageConverter smart
                    ? smart.fromMessage(message, TASK_TYPE)
                    : messageConverter.fromMessage(message);
            return payload instanceof AsyncOrderTaskDto task ? task : null;
        } catch (RuntimeException conversionError) {
            logger.warn("【Consumer】無法解析線上訂單訊息內容: {}", conversionError.getMessage());
            return null;
        }
    }

    private Optional<PlaceOnlineOrderHandler.Result> findExistingQuietly(AsyncOrderTaskDto task) {
        try {
            return placeOnlineOrderHandler.findExisting(task.getBrandId(), task.getRequestId());
        } catch (Exception lookupError) {
            logger.warn("【Consumer】查詢既有訂單失敗 (RequestId: {}): {}", task.getRequestId(), lookupError.getMessage());
            return Optional.empty();
        }
    }

    private void markCreatedQuietly(AsyncOrderTaskDto task, PlaceOnlineOrderHandler.Result result) {
        try {
            ticketService.markCreated(task.getRequestId(), task.getBrandId(), task.getRequester(),
                    result.orderId(), result.orderNumber());
        } catch (Exception redisError) {
            logger.warn("【Consumer】更新 ticket 為 CREATED 失敗 (RequestId: {}): {}",
                    task.getRequestId(), redisError.getMessage());
        }
    }

    // 移除：markFailedQuietly()（每次嘗試失敗都標 FAILED 會造成 FAILED → CREATED 翻轉），
    // FAILED 改由 recover() 在最終失敗時統一標記。
}
