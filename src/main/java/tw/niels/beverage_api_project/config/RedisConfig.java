package tw.niels.beverage_api_project.config;

import org.springframework.context.annotation.Configuration;


@Configuration
public class RedisConfig {
    // 這裡目前留空，依賴 Spring Boot 自動配置的 RedisTemplate 即可
    // 未來可用於設定 Redis Cache Manager 或自訂序列化器
}