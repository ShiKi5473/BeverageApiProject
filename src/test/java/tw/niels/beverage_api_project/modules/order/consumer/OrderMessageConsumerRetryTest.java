package tw.niels.beverage_api_project.modules.order.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.PlaceOnlineOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 線上訂單消費者重試分類與最終失敗處理的單元測試（新增，m-1）。
 *
 * 使用與正式環境相同的組件：RabbitConfig.onlineOrderRetryInterceptor（分類重試）+
 * RabbitConfig.onlineOrderRecoverer（最終失敗處理）+ OrderMessageConsumer，
 * 以 Spring AOP 代理模擬 listener container 呼叫監聽者的方式（參數為 channel、message），驗證：
 * - 暫時性錯誤會重試，重試成功時 ticket 從未被標為 FAILED
 * - 暫時性錯誤重試用盡：只在最後一次失敗後標記一次 FAILED，並以 AmqpRejectAndDontRequeueException 轉入 DLQ
 * - 業務錯誤（AppException / DomainException）不重試，直接標記 FAILED（附業務訊息）並轉入 DLQ
 * - 失敗後發現訂單其實已存在 → 標記 CREATED，訊息正常確認
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderMessageConsumer 重試分類與 ticket 最終狀態")
class OrderMessageConsumerRetryTest {

    private static final String TICKET_ID = "TICKET-001";
    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 10L;
    private static final String REQUESTER = "GUEST:abc";
    private static final String GENERIC_FAILURE = "訂單建立失敗，請稍後再試";

    @Mock private PlaceOnlineOrderHandler placeOnlineOrderHandler;
    @Mock private OnlineOrderTicketService ticketService;

    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    private OrderMessageConsumer consumer;
    private AsyncOrderTaskDto task;
    private Message message;
    /** 經過重試攔截器的監聽者呼叫（模擬 container 的 invokeListener(channel, message)） */
    private ListenerInvoker listener;

    /** 模擬 listener container 呼叫監聽者的介面（第二個參數必須是 Message，recoverer 由此取得原始訊息） */
    interface ListenerInvoker {
        void invoke(Object channel, Message message);
    }

    @BeforeEach
    void setUp() {
        consumer = new OrderMessageConsumer(placeOnlineOrderHandler, ticketService, converter);
        task = new AsyncOrderTaskDto(TICKET_ID, BRAND_ID, STORE_ID, REQUESTER, null, REQUESTER, List.of(), "少冰");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(TICKET_ID);
        message = converter.toMessage(task, properties);

        // 與正式環境相同：最多 3 次，間隔縮短為 1ms 以加速測試
        RabbitProperties.ListenerRetry retry = new RabbitProperties.ListenerRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setInitialInterval(Duration.ofMillis(1));
        retry.setMultiplier(1.0);
        retry.setMaxInterval(Duration.ofMillis(1));
        RetryOperationsInterceptor interceptor = RabbitConfig.onlineOrderRetryInterceptor(
                retry, RabbitConfig.onlineOrderRecoverer(() -> consumer));

        ListenerInvoker target = (channel, msg) -> consumer.handleOrderMessage(task);
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(ListenerInvoker.class);
        proxyFactory.setTarget(target);
        proxyFactory.addAdvice(interceptor);
        listener = (ListenerInvoker) proxyFactory.getProxy();
    }

    private static PlaceOnlineOrderHandler.Result createdResult() {
        return new PlaceOnlineOrderHandler.Result(500L, "10-20261017-0001", "AWAITING_ACCEPTANCE", true);
    }

    @Test
    @DisplayName("暫時性錯誤重試後成功：ticket 從未被標為 FAILED，最後標記 CREATED")
    void transientFailureThenSuccess_shouldNeverMarkFailed() {
        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new DataAccessResourceFailureException("DB 暫時無法連線"))
                .thenThrow(new DataAccessResourceFailureException("DB 暫時無法連線"))
                .thenReturn(createdResult());

        assertThatCode(() -> listener.invoke(null, message)).doesNotThrowAnyException();

        verify(placeOnlineOrderHandler, times(3)).handle(any(PlaceOnlineOrderCommand.class));
        verify(ticketService, never()).markFailed(anyString(), any(), any(), any());
        verify(ticketService).markCreated(TICKET_ID, BRAND_ID, REQUESTER, 500L, "10-20261017-0001");
    }

    @Test
    @DisplayName("暫時性錯誤重試用盡：只在最後一次失敗後標記一次 FAILED，並 reject 不 requeue（轉入 DLQ）")
    void transientFailureExhausted_shouldMarkFailedOnceAfterLastAttemptAndRejectToDlq() {
        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new DataAccessResourceFailureException("DB 暫時無法連線"));

        assertThatThrownBy(() -> listener.invoke(null, message))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        // 3 次嘗試都完成之後才標記 FAILED，而且只標記一次（通用訊息，不外洩內部錯誤）
        InOrder order = inOrder(placeOnlineOrderHandler, ticketService);
        order.verify(placeOnlineOrderHandler, times(3)).handle(any(PlaceOnlineOrderCommand.class));
        order.verify(ticketService).markFailed(TICKET_ID, BRAND_ID, REQUESTER, GENERIC_FAILURE);
        verify(ticketService, times(1)).markFailed(anyString(), any(), any(), any());
        verify(ticketService, never()).markCreated(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("業務錯誤（找不到商品）不重試：只呼叫一次，立即標記 FAILED（附業務訊息）並轉入 DLQ")
    void businessError_shouldNotRetryAndMarkFailedWithBusinessMessage() {
        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new ResourceNotFoundException("找不到商品，ID：999"));

        assertThatThrownBy(() -> listener.invoke(null, message))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(placeOnlineOrderHandler, times(1)).handle(any(PlaceOnlineOrderCommand.class));
        verify(ticketService, times(1)).markFailed(TICKET_ID, BRAND_ID, REQUESTER, "找不到商品，ID：999");
    }

    @Test
    @DisplayName("DomainException（422）同樣不重試")
    void domainException_shouldNotRetry() {
        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new DomainException("商品已停售"));

        assertThatThrownBy(() -> listener.invoke(null, message))
                .isInstanceOf(ListenerExecutionFailedException.class);

        verify(placeOnlineOrderHandler, times(1)).handle(any(PlaceOnlineOrderCommand.class));
        verify(ticketService).markFailed(TICKET_ID, BRAND_ID, REQUESTER, "商品已停售");
    }

    @Test
    @DisplayName("失敗後發現訂單其實已建立（唯一鍵競爭）：標記 CREATED、不重試、不標 FAILED")
    void failureButOrderAlreadyExists_shouldMarkCreated() {
        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new DataIntegrityViolationException("uk_orders_request_id"));
        when(placeOnlineOrderHandler.findExisting(BRAND_ID, TICKET_ID))
                .thenReturn(Optional.of(new PlaceOnlineOrderHandler.Result(
                        500L, "10-20261017-0001", "AWAITING_ACCEPTANCE", false)));

        assertThatCode(() -> listener.invoke(null, message)).doesNotThrowAnyException();

        verify(placeOnlineOrderHandler, times(1)).handle(any(PlaceOnlineOrderCommand.class));
        verify(ticketService).markCreated(TICKET_ID, BRAND_ID, REQUESTER, 500L, "10-20261017-0001");
        verify(ticketService, never()).markFailed(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("最終失敗處理時訂單已存在：recover 回傳 true（訊息確認、不進 DLQ）並標記 CREATED")
    void recover_whenOrderExists_shouldMarkCreatedAndAck() {
        when(placeOnlineOrderHandler.findExisting(BRAND_ID, TICKET_ID))
                .thenReturn(Optional.of(new PlaceOnlineOrderHandler.Result(
                        500L, "10-20261017-0001", "AWAITING_ACCEPTANCE", false)));

        boolean resolved = consumer.recover(message, new DataAccessResourceFailureException("timeout"));

        assertThat(resolved).isTrue();
        verify(ticketService).markCreated(TICKET_ID, BRAND_ID, REQUESTER, 500L, "10-20261017-0001");
        verify(ticketService, never()).markFailed(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("毒訊息（body 不是合法 JSON）：以 messageId 找到 ticket 標記 FAILED，回傳 false（轉入 DLQ）")
    void recover_withUnparseableBody_shouldFallBackToMessageId() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(TICKET_ID);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message poison = new Message("not-json".getBytes(StandardCharsets.UTF_8), properties);

        boolean resolved = consumer.recover(poison, new MessageConversionException("bad json"));

        assertThat(resolved).isFalse();
        verify(ticketService).markFailed(eq(TICKET_ID), eq(null), eq(null), eq(GENERIC_FAILURE));
    }

    @Test
    @DisplayName("重試分類：業務錯誤 / 轉換錯誤 / AmqpRejectAndDontRequeueException（含被包裝時）不重試，其他例外可重試")
    void isRetryable_shouldClassifyByCauseChain() {
        Message msg = new Message(new byte[0], new MessageProperties());

        assertThat(RabbitConfig.isRetryable(new ListenerExecutionFailedException("listener failed",
                new AmqpRejectAndDontRequeueException("business", new ResourceNotFoundException("x")), msg))).isFalse();
        assertThat(RabbitConfig.isRetryable(new ListenerExecutionFailedException("listener failed",
                new DomainException("422"), msg))).isFalse();
        assertThat(RabbitConfig.isRetryable(new ListenerExecutionFailedException("convert failed",
                new MessageConversionException("bad json"), msg))).isFalse();

        assertThat(RabbitConfig.isRetryable(new ListenerExecutionFailedException("listener failed",
                new DataAccessResourceFailureException("db down"), msg))).isTrue();
        assertThat(RabbitConfig.isRetryable(new IllegalStateException("redis down"))).isTrue();
    }

    @Test
    @DisplayName("retry.enabled=false：只嘗試 1 次，仍會標記 FAILED 並轉入 DLQ")
    void retryDisabled_shouldAttemptOnceAndStillMarkFailed() {
        RabbitProperties.ListenerRetry disabled = new RabbitProperties.ListenerRetry();
        disabled.setEnabled(false);
        RetryOperationsInterceptor interceptor = RabbitConfig.onlineOrderRetryInterceptor(
                disabled, RabbitConfig.onlineOrderRecoverer(() -> consumer));
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(ListenerInvoker.class);
        proxyFactory.setTarget((ListenerInvoker) (channel, msg) -> consumer.handleOrderMessage(task));
        proxyFactory.addAdvice(interceptor);
        ListenerInvoker noRetryListener = (ListenerInvoker) proxyFactory.getProxy();

        when(placeOnlineOrderHandler.handle(any(PlaceOnlineOrderCommand.class)))
                .thenThrow(new DataAccessResourceFailureException("DB 暫時無法連線"));

        assertThatThrownBy(() -> noRetryListener.invoke(null, message))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(placeOnlineOrderHandler, times(1)).handle(any(PlaceOnlineOrderCommand.class));
        verify(ticketService, times(1)).markFailed(TICKET_ID, BRAND_ID, REQUESTER, GENERIC_FAILURE);
    }
}
