package tw.niels.beverage_api_project.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // 定義交換機名稱
    public static final String KDS_EXCHANGE = "kds.exchange";
    public static final String DLQ_EXCHANGE = "dlq.exchange";
    public static final String DLQ_QUEUE = "dlq.queue";

    /**
     * 使用 JSON 序列化訊息，而非預設的 Java Serialization
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
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
}