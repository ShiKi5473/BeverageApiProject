package tw.niels.beverage_api_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import tw.niels.beverage_api_project.modules.kds.service.KdsService;

@Configuration
public class RedisConfig {

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 訂閱 "kds-events" 頻道
        container.addMessageListener(listenerAdapter, new PatternTopic("kds-events"));
        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(KdsService kdsService) {
        // 當收到訊息時，呼叫 KdsService 的 handleRedisMessage 方法
        return new MessageListenerAdapter(kdsService, "handleRedisMessage");
    }
}
