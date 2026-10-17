package tw.niels.beverage_api_project.modules.order.application.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tw.niels.beverage_api_project.common.util.TsidUtil;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.application.command.SubmitOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

/**
 * 送出線上訂單 Handler（新增，B-4）
 *
 * 從 OnlineOrderController 抽出的應用層邏輯（Controller 只負責 HTTP 轉換）：
 * 1. 驗證分店屬於該品牌（訪客 / 會員的 storeId 來自請求 body）
 * 2. 產生 ticketId，並在 Redis 建立 QUEUED 狀態的 ticket（記錄下單者，供查詢時驗證本人）
 * 3. 把任務送入 RabbitMQ 佇列（實際建單由 OrderMessageConsumer → PlaceOnlineOrderHandler 處理）
 *
 * 修改（m-4 發送確認）：
 * 原本 convertAndSend 沒有帶 CorrelationData，publisher confirm 的 nack 與 mandatory 的 return
 * 只在 RabbitConfig 的全域 callback 記錄日誌，ticket 會一直停在 QUEUED 直到 24 小時過期。
 * 現在以 CorrelationData(ticketId) 發送，並在該筆訊息自己的 confirm future 完成時判斷：
 * - broker nack            → ticket 標為 FAILED
 * - 無法路由（returned）    → ticket 標為 FAILED（Spring AMQP 保證 returned 會在 future 完成前設定）
 * - ack 且未被退回          → 維持 QUEUED，等待消費者處理
 * 確認是非同步的，HTTP 回應不需要等待 broker（POST /online-orders 仍回 202 QUEUED，前端輪詢會看到 FAILED）；
 * 也不需要在 RabbitConfig 的全域 callback 注入 OnlineOrderTicketService，避免循環依賴。
 * 另外把 AMQP messageId 設為 ticketId：消費端在訊息 body 無法解析時，仍能以 messageId 找到 ticket 標記 FAILED。
 *
 * 不加 @Transactional：這裡沒有寫入資料庫（分店查詢由 StoreService 自己的唯讀交易處理）。
 */
@Component
public class SubmitOnlineOrderHandler {

    private static final Logger logger = LoggerFactory.getLogger(SubmitOnlineOrderHandler.class);

    /** 發送失敗時給前端的訊息 */
    static final String MSG_BROKER_FAILURE = "系統繁忙，請稍後再試";

    private final StoreService storeService;
    private final OnlineOrderTicketService ticketService;
    private final RabbitTemplate rabbitTemplate;

    public SubmitOnlineOrderHandler(StoreService storeService,
                                    OnlineOrderTicketService ticketService,
                                    RabbitTemplate rabbitTemplate) {
        this.storeService = storeService;
        this.ticketService = ticketService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 送出線上訂單（非同步）。
     *
     * @param command 送出命令
     * @return ticketId（同時作為訂單的 request_id）
     */
    public String handle(SubmitOnlineOrderCommand command) {
        // 1. 分店必須屬於該品牌（找不到則 404）
        storeService.getStore(command.brandId(), command.storeId());

        // 2. 產生 ticket 並先寫入 QUEUED（必須在送出訊息前，避免消費者先寫入 CREATED 又被覆蓋）
        String ticketId = TsidUtil.nextIdString();
        ticketService.createQueued(ticketId, command.brandId(), command.requester());

        AsyncOrderTaskDto task = new AsyncOrderTaskDto(
                ticketId,
                command.brandId(),
                command.storeId(),
                command.requester(),
                command.memberId(),
                command.guestRef(),
                command.items(),
                command.customerNote()
        );

        // 3. 發送到 RabbitMQ；同步失敗（連線中斷等）時把 ticket 標為 FAILED 並回 503
        // 新增（m-4）：帶 CorrelationData(ticketId) 以取得這筆訊息的 broker 確認結果
        CorrelationData correlation = new CorrelationData(ticketId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.ONLINE_ORDER_EXCHANGE,
                    RabbitConfig.ONLINE_ORDER_ROUTING_KEY,
                    task,
                    message -> {
                        // 新增：messageId = ticketId（消費端最終失敗處理的備援識別、也方便在 DLQ 追查）
                        message.getMessageProperties().setMessageId(ticketId);
                        return message;
                    },
                    correlation
            );
            logger.info("線上訂單請求已發送至佇列，Ticket ID: {}", ticketId);
        } catch (AmqpException e) {
            logger.error("發送 RabbitMQ 失敗，Ticket ID: {}", ticketId, e);
            ticketService.markFailed(ticketId, command.brandId(), command.requester(), MSG_BROKER_FAILURE);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MSG_BROKER_FAILURE, e);
        }

        // 4. 新增（m-4）：broker 非同步回覆確認後，nack 或無法路由就把 ticket 標為 FAILED
        //    （回呼在 AMQP 連線執行緒上執行，只做一次 Redis 寫入，不阻塞 HTTP 請求）
        correlation.getFuture().whenComplete((confirm, error) ->
                onPublishConfirm(ticketId, command.brandId(), command.requester(), correlation, confirm, error));
        return ticketId;
    }

    /**
     * 新增（m-4）：處理單筆線上訂單訊息的 publisher confirm 結果。
     *
     * @param ticketId     ticket ID
     * @param brandId      品牌 ID
     * @param requester    下單者 principalKey
     * @param correlation  發送時使用的 CorrelationData（被退回時 getReturned() 不為 null）
     * @param confirm      broker 確認結果（ack / nack）
     * @param error        future 例外完成時的原因（正常情況為 null）
     */
    void onPublishConfirm(String ticketId, Long brandId, String requester,
                          CorrelationData correlation, CorrelationData.Confirm confirm, Throwable error) {
        try {
            if (error != null) {
                // 無法得知 broker 是否收到：保持 QUEUED（若消費者已處理會寫入 CREATED），只記錄警告
                logger.warn("線上訂單發送確認結果未知，Ticket ID: {}: {}", ticketId, error.getMessage());
                return;
            }
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                // mandatory=true：沒有任何佇列綁定 → 訊息被退回，消費者永遠收不到
                logger.error("線上訂單訊息無法路由，標記 ticket 為 FAILED，Ticket ID: {}, replyCode={}, replyText={}",
                        ticketId, returned.getReplyCode(), returned.getReplyText());
                ticketService.markFailed(ticketId, brandId, requester, MSG_BROKER_FAILURE);
                return;
            }
            if (confirm != null && !confirm.isAck()) {
                // broker 拒收（nack）：訊息未被保存
                logger.error("線上訂單訊息被 broker 拒收(nack)，標記 ticket 為 FAILED，Ticket ID: {}, cause={}",
                        ticketId, confirm.getReason());
                ticketService.markFailed(ticketId, brandId, requester, MSG_BROKER_FAILURE);
            }
        } catch (RuntimeException e) {
            // 在 AMQP 執行緒上執行，例外不能往外拋（會中斷其他確認回呼）
            logger.warn("處理線上訂單發送確認時更新 ticket 失敗，Ticket ID: {}: {}", ticketId, e.getMessage());
        }
    }
}
