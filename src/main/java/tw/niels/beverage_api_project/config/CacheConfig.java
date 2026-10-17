package tw.niels.beverage_api_project.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 修正：預設的 GenericJackson2JsonRedisSerializer 沒有註冊 JavaTimeModule，
        // 快取內容只要含 LocalDate / LocalDateTime / Instant 就會序列化失敗。
        // 這裡沿用其內建的 ObjectMapper（含 default typing），僅額外註冊 JavaTimeModule。
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer()
                .configure(mapper -> mapper.registerModule(new JavaTimeModule()));

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 預設快取 10 分鐘
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        // 修正：加上 transactionAware()，讓 @CacheEvict / put 延後到交易提交後才執行，
        // 避免「已清快取、交易尚未提交」期間被並行讀取把舊資料重新寫回快取。
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
}