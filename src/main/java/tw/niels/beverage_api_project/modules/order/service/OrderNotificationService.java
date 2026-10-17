package tw.niels.beverage_api_project.modules.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tw.niels.beverage_api_project.config.AsyncConfig;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderCancelledEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderPlacedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderStatusChangedEvent;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.dto.OrderNotificationDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.util.Optional;

/**
 * 訂單通知服務：監聽訂單 domain event，並透過 WebSocket（STOMP 個人頻道 /user/queue/orders）通知顧客。
 * <p>
 * 【P0-4 重寫】
 * <ul>
 *   <li>原本只監聽舊的 {@code OrderStateChangedEvent}（DDD Handler 從不發布）→ 顧客永遠收不到通知。
 *       改為監聽與 KDS 相同的四種 domain event：{@link OrderStatusChangedEvent}、{@link OrderPlacedEvent}、
 *       {@link OrderClosedEvent}、{@link OrderCancelledEvent}。</li>
 *   <li>收件者改為 principalKey：有會員時為 {@code "U:" + memberId}，否則為訂單的 {@code guestRef}（"GUEST:uuid"），
 *       兩者都沒有（一般 POS 散客）則略過。原本使用手機號碼，跨品牌同號會收到彼此的通知。
 *       與 {@code JwtAuthChannelInterceptor} 設定的 STOMP session 使用者名稱（StompPrincipal#getName）一致。</li>
 *   <li>AFTER_COMMIT 後於 kdsExecutor 非同步執行，並在唯讀新交易中以 {@code findByIdForSystem} 載入訂單，
 *       避免在 @Async 執行緒讀取游離實體的延遲關聯。</li>
 * </ul>
 * </p>
 */
@Service
public class OrderNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(OrderNotificationService.class);

    /** STOMP 個人頻道（前端訂閱 /user/queue/orders） */
    private static final String USER_DESTINATION = "/queue/orders";
    /** 會員 principalKey 前綴（與 AppUserDetails#principalKey 一致） */
    private static final String MEMBER_KEY_PREFIX = "U:";

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderJpaRepository orderJpaRepository;
    // 程式化唯讀交易：避免 @Transactional 自我呼叫不生效；REQUIRES_NEW 確保 CallerRunsPolicy 時也開新交易
    private final TransactionTemplate readOnlyTransaction;

    public OrderNotificationService(SimpMessagingTemplate messagingTemplate,
                                    OrderJpaRepository orderJpaRepository,
                                    PlatformTransactionManager transactionManager) {
        this.messagingTemplate = messagingTemplate;
        this.orderJpaRepository = orderJpaRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 狀態變更（結帳／接單後製作中、製作完成）。
     * PENDING／HELD 不通知；AWAITING_ACCEPTANCE、CLOSED、CANCELLED 由專屬事件處理，這裡略過以避免重複通知。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus newStatus = event.newStatus();
        if (newStatus != OrderStatus.PREPARING && newStatus != OrderStatus.READY_FOR_PICKUP) {
            return;
        }
        notifyCustomer(event.orderId(), newStatus, null);
    }

    /**
     * 訂單建立：只有線上訂單（載入後狀態為 AWAITING_ACCEPTANCE）才通知「已送出，等待店家接單」。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notifyCustomer(event.orderId(), OrderStatus.AWAITING_ACCEPTANCE, OrderStatus.AWAITING_ACCEPTANCE);
    }

    /**
     * 訂單完成（顧客已取餐）。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderClosed(OrderClosedEvent event) {
        notifyCustomer(event.orderId(), OrderStatus.CLOSED, null);
    }

    /**
     * 訂單取消。
     */
    @Async(AsyncConfig.KDS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notifyCustomer(event.orderId(), OrderStatus.CANCELLED, null);
    }

    /**
     * 載入訂單、決定收件者並送出 STOMP 個人通知。
     *
     * @param orderId        訂單 ID
     * @param status         通知內容代表的狀態
     * @param requiredStatus 若不為 null，只有載入後訂單狀態等於此值才通知
     */
    private void notifyCustomer(Long orderId, OrderStatus status, OrderStatus requiredStatus) {
        if (orderId == null) {
            logger.warn("訂單通知事件缺少 orderId，略過");
            return;
        }
        try {
            Optional<Recipient> loaded = loadRecipient(orderId);
            if (loaded.isEmpty()) {
                logger.warn("訂單通知時找不到訂單 {}", orderId);
                return;
            }
            Recipient recipient = loaded.get();
            // JPA 與 domain 各有一個 OrderStatus enum（常數名稱相同），以名稱比較
            if (requiredStatus != null && !requiredStatus.name().equals(recipient.currentStatus())) {
                return;
            }
            if (!StringUtils.hasText(recipient.principalKey())) {
                // 沒有會員也沒有訪客識別（一般 POS 散客）：沒有可推播的對象
                logger.debug("訂單 {} 無會員或訪客識別，跳過個人通知", recipient.orderNumber());
                return;
            }

            OrderNotificationDto notification = new OrderNotificationDto(
                    recipient.orderNumber(),
                    status.name(),
                    formatMessage(recipient.orderNumber(), status)
            );

            // 推送到 STOMP 個人頻道：/user/{principalKey}/queue/orders（前端訂閱 /user/queue/orders）
            messagingTemplate.convertAndSendToUser(recipient.principalKey(), USER_DESTINATION, notification);
            logger.debug("已發送訂單通知: 訂單 {} -> {}", recipient.orderNumber(), status);
        } catch (MessagingException e) {
            // 推播失敗不影響訂單（已提交），只記錄
            logger.error("訂單通知推播失敗 (訂單: {}, 狀態: {})", orderId, status, e);
        } catch (RuntimeException e) {
            // 非同步監聽器的例外不會回到呼叫端，這裡記錄後吞掉
            logger.error("訂單通知處理失敗 (訂單: {}, 狀態: {})", orderId, status, e);
        }
    }

    /**
     * 在唯讀新交易中載入訂單並取出收件者資訊（交易外不再碰 JPA 實體）。
     */
    private Optional<Recipient> loadRecipient(Long orderId) {
        Recipient result = readOnlyTransaction.execute(txStatus ->
                orderJpaRepository.findByIdForSystem(orderId)
                        .map(OrderNotificationService::toRecipient)
                        .orElse(null));
        return Optional.ofNullable(result);
    }

    private static Recipient toRecipient(OrderEntity order) {
        String principalKey;
        if (order.getMember() != null && order.getMember().getId() != null) {
            // 會員：與 AppUserDetails#principalKey（"U:" + userId）一致
            principalKey = MEMBER_KEY_PREFIX + order.getMember().getId();
        } else {
            // 訪客：線上訂單建立時寫入的 "GUEST:uuid"（與 GuestPrincipal#principalKey 一致）；POS 散客為 null
            principalKey = order.getGuestRef();
        }
        String currentStatus = order.getStatus() != null ? order.getStatus().name() : null;
        return new Recipient(principalKey, order.getOrderNumber(), currentStatus);
    }

    /**
     * 交易內取出的收件者資訊。
     */
    private record Recipient(String principalKey, String orderNumber, String currentStatus) {
    }

    private String formatMessage(String orderNumber, OrderStatus status) {
        return switch (status) {
            case AWAITING_ACCEPTANCE -> String.format("您的訂單 %s 已送出，等待店家接單", orderNumber);
            case PREPARING -> String.format("您的訂單 %s 正在製作中", orderNumber);
            case READY_FOR_PICKUP -> String.format("您的訂單 %s 已製作完成，請至櫃檯取餐！", orderNumber);
            case CLOSED -> String.format("您的訂單 %s 已結案，感謝您的光臨", orderNumber);
            case CANCELLED -> String.format("您的訂單 %s 已取消", orderNumber);
            default -> String.format("您的訂單 %s 狀態已更新為 %s", orderNumber, status.name());
        };
    }
}
