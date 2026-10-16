package tw.niels.beverage_api_project.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with("dead.letter");
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
}