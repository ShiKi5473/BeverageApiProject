package tw.niels.beverage_api_project;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    // 優化 1: 改用 Alpine 版本，記憶體佔用較低
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(2)); // 延長等待時間

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:alpine"));

    @Container
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data")
            .withExposedPorts(9000);

    // 優化 2: 移除 -management 後綴，改用純核心版本 (節省約 100MB+ RAM)
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL Config
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // 優化 3: 顯式設定連線池大小，避免測試並發搶佔資源
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");

        // Redis Config
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        // 增加 Redis 超時設定
        registry.add("spring.data.redis.timeout", () -> "5000");

        // RabbitMQ Config
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

        // MinIO Config
        registry.add("minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket-name", () -> "test-bucket");
    }
}