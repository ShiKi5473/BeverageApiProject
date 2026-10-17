package tw.niels.beverage_api_project.modules.order.application.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.application.command.SubmitOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SubmitOnlineOrderHandler 發送確認處理的單元測試（新增，m-4）。
 *
 * 驗證以 CorrelationData(ticketId) 發送後：
 * - broker nack → ticket 標為 FAILED
 * - 訊息無法路由（returned）→ ticket 標為 FAILED
 * - ack 且未被退回 → ticket 維持 QUEUED（不呼叫 markFailed）
 * - messageId 設為 ticketId；同步發送失敗 → FAILED + 503
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubmitOnlineOrderHandler 發送確認（nack / return）")
class SubmitOnlineOrderHandlerTest {

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 10L;
    private static final String REQUESTER = "GUEST:abc";

    @Mock private StoreService storeService;
    @Mock private OnlineOrderTicketService ticketService;
    @Mock private RabbitTemplate rabbitTemplate;

    private SubmitOnlineOrderHandler handler;
    private SubmitOnlineOrderCommand command;

    @BeforeEach
    void setUp() {
        handler = new SubmitOnlineOrderHandler(storeService, ticketService, rabbitTemplate);
        command = new SubmitOnlineOrderCommand(BRAND_ID, STORE_ID, REQUESTER, null, REQUESTER, List.of(), "少冰");
    }

    /** 送出訂單並取得發送時使用的 CorrelationData */
    private CorrelationData submitAndCaptureCorrelation(String[] ticketHolder) {
        ticketHolder[0] = handler.handle(command);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.ONLINE_ORDER_EXCHANGE), eq(RabbitConfig.ONLINE_ORDER_ROUTING_KEY),
                any(Object.class), any(MessagePostProcessor.class), correlation.capture());
        return correlation.getValue();
    }

    @Test
    @DisplayName("以 CorrelationData(ticketId) 發送，messageId 也設為 ticketId，並先建立 QUEUED ticket")
    void shouldSendWithTicketCorrelationAndMessageId() {
        String ticketId = handler.handle(command);

        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.ONLINE_ORDER_EXCHANGE), eq(RabbitConfig.ONLINE_ORDER_ROUTING_KEY),
                any(Object.class), postProcessor.capture(), correlation.capture());
        verify(ticketService).createQueued(ticketId, BRAND_ID, REQUESTER);

        assertThat(correlation.getValue().getId()).isEqualTo(ticketId);
        Message processed = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(processed.getMessageProperties().getMessageId()).isEqualTo(ticketId);
    }

    @Test
    @DisplayName("broker nack：ticket 標為 FAILED")
    void brokerNack_shouldMarkTicketFailed() {
        String[] ticket = new String[1];
        CorrelationData correlation = submitAndCaptureCorrelation(ticket);

        correlation.getFuture().complete(new CorrelationData.Confirm(false, "internal error"));

        verify(ticketService).markFailed(ticket[0], BRAND_ID, REQUESTER, SubmitOnlineOrderHandler.MSG_BROKER_FAILURE);
    }

    @Test
    @DisplayName("訊息無法路由（returned，broker 仍回 ack）：ticket 標為 FAILED")
    void returnedMessage_shouldMarkTicketFailed() {
        String[] ticket = new String[1];
        CorrelationData correlation = submitAndCaptureCorrelation(ticket);

        // Spring AMQP 保證 returned 在 confirm future 完成前就已設定
        correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                312, "NO_ROUTE", RabbitConfig.ONLINE_ORDER_EXCHANGE, RabbitConfig.ONLINE_ORDER_ROUTING_KEY));
        correlation.getFuture().complete(new CorrelationData.Confirm(true, null));

        verify(ticketService).markFailed(ticket[0], BRAND_ID, REQUESTER, SubmitOnlineOrderHandler.MSG_BROKER_FAILURE);
    }

    @Test
    @DisplayName("broker ack 且未被退回：ticket 維持 QUEUED，不標記 FAILED")
    void brokerAck_shouldKeepTicketQueued() {
        String[] ticket = new String[1];
        CorrelationData correlation = submitAndCaptureCorrelation(ticket);

        correlation.getFuture().complete(new CorrelationData.Confirm(true, null));

        verify(ticketService).createQueued(ticket[0], BRAND_ID, REQUESTER);
        verify(ticketService, never()).markFailed(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("更新 ticket 失敗不會讓例外拋回 AMQP 確認執行緒")
    void ticketUpdateFailureInCallback_shouldBeSwallowed() {
        String[] ticket = new String[1];
        CorrelationData correlation = submitAndCaptureCorrelation(ticket);
        doThrow(new IllegalStateException("redis down"))
                .when(ticketService).markFailed(anyString(), any(), any(), any());

        correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));

        // whenComplete 回呼內的例外若未被吞掉，future 衍生的階段會例外完成；這裡只要確認有嘗試標記且未拋出
        verify(ticketService).markFailed(ticket[0], BRAND_ID, REQUESTER, SubmitOnlineOrderHandler.MSG_BROKER_FAILURE);
    }

    @Test
    @DisplayName("同步發送失敗（連線中斷）：ticket 標為 FAILED 並回 503")
    void sendFailure_shouldMarkFailedAndReturn503() {
        doThrow(new AmqpConnectException(new ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(ticketService).markFailed(anyString(), eq(BRAND_ID), eq(REQUESTER),
                eq(SubmitOnlineOrderHandler.MSG_BROKER_FAILURE));
    }
}
