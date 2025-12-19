package tw.niels.beverage_api_project;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    // 宣告為 static final，但移除 @Container 註解
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-alpine")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true); // 啟用 Reuse (雖然在 CI 環境通常無效，但本地有用)

    static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:alpine"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true);

    static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true);

    static final RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-alpine"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true);

    // 使用 static block 確保容器只啟動一次
    static {
        // 並行啟動容器以節省時間 (使用 parallel stream)
        // 注意：這需要您的機器有足夠的 CPU，如果 CI 只有 2核，依序啟動可能更穩定
        // 這裡我們採用依序啟動最保險
        postgres.start();
        redis.start();
        minio.start();
        rabbitmq.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL Config
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // 【關鍵優化】限制連線池大小，避免搶佔資源
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        // 延長連線超時，避免 CI CPU 忙碌時連線失敗
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");

        // Redis Config
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        // 增加 Redis 超時設定
        registry.add("spring.data.redis.timeout", () -> "10s");

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

    // 注入 JdbcTemplate 用於執行原生 SQL
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 全域清理方法：在每個測試方法結束後執行。
     * 使用 TRUNCATE ... CASCADE 可以忽略外鍵約束，一次清空所有資料。
     */
    @AfterEach
    public void cleanupDatabase() {
        // 列出所有需要清理的業務表
        // 注意：不要包含 flyway_schema_history 或 payment_methods (如果是靜態字典表)
        // 這裡包含了您專案中主要的動態資料表
        String truncateSql = "TRUNCATE TABLE " +
                "order_items, orders, " +
                "inventory_transactions, inventory_snapshots, inventory_batches, purchase_shipments, inventory_items, " +
                "product_options, product_variants, recipes, products, option_groups, categories, " +
                "staff_profiles, member_profiles, users, " +
                "stores, brands, " +
                "promotions, " +
                "daily_store_stats, daily_product_stats " +
                "CASCADE"; // CASCADE 是關鍵，它會自動處理關聯刪除

        jdbcTemplate.execute(truncateSql);
    }
}