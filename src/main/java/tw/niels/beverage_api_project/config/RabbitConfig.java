package tw.niels.beverage_api_project.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.niels.beverage_api_project.common.exception.AppException;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.modules.order.consumer.OrderMessageConsumer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    // KDS 廣播用
    public static final String KDS_EXCHANGE = "kds.exchange";

    // 死信佇列設定
    public static final String DLQ_EXCHANGE = "dlq.exchange";
    public static final String DLQ_QUEUE = "dlq.queue";
    public static final String DLQ_ROUTING_KEY = "dead.letter";

    // 線上訂單削峰填谷設定
    public static final String ONLINE_ORDER_EXCHANGE = "online.order.exchange";
    public static final String ONLINE_ORDER_QUEUE = "online.order.queue";
    public static final String ONLINE_ORDER_ROUTING_KEY = "create.order";

    /**
     * 新增（m-1）：線上訂單專用的 listener container factory 名稱。
     * OrderMessageConsumer 以 @RabbitListener(containerFactory = ...) 指定使用，
     * 其他監聽者（例如 KDS 廣播）仍使用 Spring Boot 自動配置的預設 rabbitListenerContainerFactory，不受影響。
     */
    public static final String ONLINE_ORDER_LISTENER_FACTORY = "onlineOrderListenerContainerFactory";

    /** 新增：例外原因鏈最多追溯的層數（防止循環 cause 造成無窮迴圈） */
    private static final int MAX_CAUSE_DEPTH = 20;

    /**
     * 使用 JSON 序列化訊息，而非預設的 Java Serialization
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 移除自訂的 RabbitTemplate Bean：Spring Boot 自動配置的 RabbitTemplate
     * 會自動套用上方的 MessageConverter Bean，並遵循 spring.rabbitmq.* 設定（含 publisher confirm/returns）。
     * 自訂 Bean 之前開了 confirm/returns 卻沒有設定任何 callback，發送失敗時完全無聲無息。
     *
     * 改用 RabbitTemplateCustomizer 在自動配置的 template 上補充：
     * - mandatory=true：訊息無法路由到任何佇列時會觸發 returns callback（而不是靜默丟棄）
     * - confirm callback：Broker 拒收(nack)時記錄錯誤
     * - returns callback：無法路由的訊息記錄錯誤
     *
     * 補充（m-4）：線上訂單 ticket 的 FAILED 標記不在這裡處理，而是由 SubmitOnlineOrderHandler
     * 以 CorrelationData(ticketId) 發送，並在該筆訊息自己的 confirm future 完成時判斷 nack / return。
     * 這樣全域 callback 不需要注入 OnlineOrderTicketService（避免 RabbitTemplate ↔ 業務 Bean 的循環依賴），
     * 這裡維持只記錄日誌（KDS 廣播等其他訊息也會走到這裡）。
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (!ack) {
                    log.error("RabbitMQ 發送確認失敗(nack)：correlationData={}, cause={}", correlationData, cause);
                } else {
                    log.debug("RabbitMQ 發送確認成功：correlationData={}", correlationData);
                }
            });
            template.setReturnsCallback(returned ->
                    log.error("RabbitMQ 訊息無法路由：exchange={}, routingKey={}, replyCode={}, replyText={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyCode(), returned.getReplyText()));
        };
    }

    // --- 1. 死信佇列 (Dead Letter Queue) 基礎建設 ---

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    /**
     * 把 DLQ 綁定到 DLX。routing key 必須與 onlineOrderQueue 的 x-dead-letter-routing-key 完全一致（DLQ_ROUTING_KEY），
     * 否則被拒絕的訊息會因為找不到綁定而直接消失。
     * 修正(B-4)：原本寫死字串 "dead.letter"，改用常數，避免兩處不一致。
     * 搭配 application.properties 的 listener retry + default-requeue-rejected=false：
     * 消費者重試 3 次仍失敗 → 訊息被 reject 且不 requeue → 由 DLX 轉進這個 DLQ。
     */
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with(DLQ_ROUTING_KEY);
    }

    // --- 2. KDS 廣播相關設定 ---

    /**
     * KDS 使用 Fanout 交換機，廣播給所有訂閱的 Queue (所有 Server 實例)
     */
    @Bean
    public FanoutExchange kdsExchange() {
        return new FanoutExchange(KDS_EXCHANGE);
    }

    /**
     * 定義一個「匿名佇列」(Anonymous Queue)。
     * 特性：獨佔 (Exclusive)、自動刪除 (Auto-Delete)、名稱隨機。
     * 用途：每個 Spring Boot 實例啟動時會擁有自己的一個 Queue，
     * 用來接收 KDS 廣播並推播給連線在該實例上的 SSE 用戶端。
     */
    @Bean
    public Queue kdsAnonymousQueue() {
        return new AnonymousQueue();
    }

    /**
     * 將匿名佇列綁定到 KDS Fanout Exchange
     */
    @Bean
    public Binding kdsBinding(FanoutExchange kdsExchange, Queue kdsAnonymousQueue) {
        return BindingBuilder.bind(kdsAnonymousQueue).to(kdsExchange);
    }

    // --- 3. 線上訂單佇列設定 ---

    @Bean
    public DirectExchange onlineOrderExchange() {
        return new DirectExchange(ONLINE_ORDER_EXCHANGE);
    }

    /**
     * 定義訂單處理佇列
     * 設定 Dead Letter Arguments：當處理失敗或被拒絕時，轉發到 DLQ
     */
    @Bean
    public Queue onlineOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        // 設定死信交換機
        args.put("x-dead-letter-exchange", DLQ_EXCHANGE);
        // 設定死信 Routing Key
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        // 設定 TTL (可選，例如訂單在 Queue 中超過 30 分鐘未處理則過期)
        // args.put("x-message-ttl", 1800000);

        return QueueBuilder.durable(ONLINE_ORDER_QUEUE)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding onlineOrderBinding() {
        return BindingBuilder.bind(onlineOrderQueue())
                .to(onlineOrderExchange())
                .with(ONLINE_ORDER_ROUTING_KEY);
    }

    // --- 4. 線上訂單消費者的重試與最終失敗處理（新增，m-1）---

    /**
     * 線上訂單專用的 listener container factory（新增，m-1）。
     *
     * 原本所有監聽者共用 Spring Boot 依 spring.rabbitmq.listener.simple.retry.* 建立的重試攔截器：
     * SimpleRetryPolicy 對「所有」例外（包含 AmqpRejectAndDontRequeueException、商品 / 分店不存在等業務錯誤）
     * 都重試 3 次，而消費者每次失敗都把 ticket 標為 FAILED，之後重試成功又變回 CREATED，
     * 前端可能在看到 FAILED 時重新下單 → 重複訂單。
     *
     * 現在這個 factory：
     * 1. 先用 Boot 的 configurer 套用所有共用設定（JSON converter、prefetch、default-requeue-rejected=false 等）
     * 2. 再以自訂的重試攔截器取代 Boot 預設的 advice chain：
     *    - 業務 / 驗證錯誤（AmqpRejectAndDontRequeueException、AppException、DomainException、訊息轉換錯誤）不重試
     *    - 暫時性錯誤（資料庫 / Redis 暫時無法連線、唯一鍵衝突等）依 properties 的次數與退避重試
     *    - 只有「最後一次」失敗（或不可重試的錯誤）才會進入 MessageRecoverer：由 OrderMessageConsumer#recover
     *      把 ticket 標為 FAILED（訂單其實已存在時改標 CREATED 並 ack），再 reject 且不 requeue → DLX → DLQ
     * 只有 OrderMessageConsumer 使用這個 factory；KDS 等其他監聽者仍使用預設的 rabbitListenerContainerFactory。
     *
     * 注意：MessageRecoverer 刻意不註冊成 Bean —— Boot 會把唯一的 MessageRecoverer Bean 套用到預設 factory，
     * 導致 KDS 廣播失敗也去更新線上訂單 ticket。消費者以 ObjectProvider 延遲取得，避免建立 factory 時產生循環依賴。
     */
    @Bean(name = ONLINE_ORDER_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory onlineOrderListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitProperties rabbitProperties,
            ObjectProvider<OrderMessageConsumer> orderMessageConsumer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 套用 spring.rabbitmq.listener.simple.* 的共用設定（含 Boot 預設的重試攔截器，下一行會被取代）
        configurer.configure(factory, connectionFactory);
        // 以分類重試 + 最終失敗處理的攔截器取代預設 advice chain（重試次數 / 退避仍沿用 properties）
        factory.setAdviceChain(onlineOrderRetryInterceptor(
                rabbitProperties.getListener().getSimple().getRetry(),
                onlineOrderRecoverer(orderMessageConsumer::getObject)));
        return factory;
    }

    /**
     * 建立線上訂單消費者的無狀態重試攔截器（新增，m-1；public static 供單元測試直接驗證）。
     *
     * - 最多嘗試次數、初始間隔、倍數、最大間隔沿用 spring.rabbitmq.listener.simple.retry.*；
     *   retry.enabled=false 時只嘗試 1 次（仍會進入 recoverer，ticket 一樣會被標為 FAILED 並轉入 DLQ）
     * - 是否重試由 {@link #isRetryable(Throwable)} 判斷（沿著 cause 鏈檢查）
     *
     * @param retry     重試設定（可為 null，視為不重試）
     * @param recoverer 最後一次失敗或不可重試時呼叫的 MessageRecoverer
     * @return 重試攔截器
     */
    public static RetryOperationsInterceptor onlineOrderRetryInterceptor(RabbitProperties.Retry retry,
                                                                         MessageRecoverer recoverer) {
        int maxAttempts = (retry != null && retry.isEnabled()) ? Math.max(1, retry.getMaxAttempts()) : 1;

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        if (retry != null) {
            if (retry.getInitialInterval() != null) {
                backOffPolicy.setInitialInterval(retry.getInitialInterval().toMillis());
            }
            backOffPolicy.setMultiplier(retry.getMultiplier());
            if (retry.getMaxInterval() != null) {
                backOffPolicy.setMaxInterval(retry.getMaxInterval().toMillis());
            }
        }

        RetryTemplate retryTemplate = new RetryTemplate();
        // 以自訂分類器取代「全部例外都重試」：不可重試的例外第一次失敗就直接進入 recoverer
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts, new ListenerExceptionClassifier()));
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate)
                .recoverer(recoverer)
                .build();
    }

    /**
     * 建立線上訂單的最終失敗處理器（新增，m-1；public static 供單元測試直接驗證）。
     *
     * 1. 交給 OrderMessageConsumer#recover 處理 ticket：
     *    訂單其實已建立 → 標記 CREATED 並回傳 true；否則標記 FAILED 並回傳 false
     * 2. 回傳 true：正常結束，重試攔截器會以 ImmediateAcknowledgeAmqpException 讓容器 ack（不進 DLQ）
     * 3. 回傳 false（或處理 ticket 時發生例外）：交給 RejectAndDontRequeueRecoverer，
     *    拋出包著 AmqpRejectAndDontRequeueException 的例外 → 容器 reject(requeue=false) → DLX → DLQ
     *
     * @param consumerSupplier 延遲取得消費者（避免建立 container factory 時就需要整個訂單 Handler 鏈）
     * @return MessageRecoverer
     */
    public static MessageRecoverer onlineOrderRecoverer(Supplier<OrderMessageConsumer> consumerSupplier) {
        MessageRecoverer rejectToDlq = new RejectAndDontRequeueRecoverer(
                () -> "線上訂單處理失敗（重試用盡或不可重試），轉入 DLQ");
        return (message, cause) -> {
            boolean resolved = false;
            try {
                resolved = consumerSupplier.get().recover(message, cause);
            } catch (RuntimeException e) {
                // ticket 更新失敗不可吞掉訊息：記錄後仍轉入 DLQ，保留人工補償的依據
                log.error("線上訂單最終失敗處理發生例外，訊息仍轉入 DLQ", e);
            }
            if (resolved) {
                return;
            }
            rejectToDlq.recover(message, cause);
        };
    }

    /**
     * 判斷線上訂單消費失敗是否值得重試（新增，m-1）。
     *
     * 沿著 cause 鏈檢查（容器會把監聽者的例外包成 ListenerExecutionFailedException）：
     * - AmqpRejectAndDontRequeueException：消費者明確表示「不要重試」（業務錯誤）
     * - AppException（400/404）、DomainException（422）：業務 / 驗證錯誤，重試結果也不會改變
     * - 訊息轉換 / 參數解析錯誤：毒訊息，重試沒有意義
     * 其他例外（資料庫、Redis 暫時失敗、唯一鍵衝突等）視為暫時性錯誤，可以重試。
     *
     * @param throwable 失敗原因
     * @return true 表示可以重試
     */
    public static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof AmqpRejectAndDontRequeueException
                    || current instanceof AppException
                    || current instanceof DomainException
                    || current instanceof org.springframework.amqp.support.converter.MessageConversionException
                    || current instanceof org.springframework.messaging.converter.MessageConversionException
                    || current instanceof MethodArgumentResolutionException) {
                return false;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return true;
    }

    /**
     * 新增：把 {@link #isRetryable(Throwable)} 接到 Spring Retry 的 SimpleRetryPolicy。
     * （預設的 BinaryExceptionClassifier 只認得類別對照表，無法表達「cause 鏈中任一層是業務錯誤就不重試」）
     */
    private static final class ListenerExceptionClassifier extends BinaryExceptionClassifier {

        ListenerExceptionClassifier() {
            super(true);
        }

        @Override
        public Boolean classify(Throwable classifiable) {
            return isRetryable(classifiable);
        }
    }
}