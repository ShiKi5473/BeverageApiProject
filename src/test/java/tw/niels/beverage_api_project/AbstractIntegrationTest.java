package tw.niels.beverage_api_project;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

// 啟用 dev profile：application.properties 已移除預設的 spring.profiles.active=dev，
// 而整合測試需要 DataSeeder（僅 dev profile）建立的種子資料與 application-dev.properties 的設定。
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    // 宣告為 static final，但移除 @Container 註解
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-alpine")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true); // 啟用 Reuse (雖然在 CI 環境通常無效，但本地有用)

    static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:alpine"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withReuse(true);

    // 物件儲存（S3 相容）。
    // 修正：MinIO 官方已停止在 Docker Hub / quay.io 公開提供映像（pull 會回 access denied / 401），
    // 新環境與 CI 都拉不到；改用公開、相容 S3 / MinIO API 的 RustFS（Apache-2.0），
    // 應用程式仍以 MinIO Java SDK 連線，設定鍵（minio.*）不變。
    // 鎖定固定版本（不用 latest），避免上游更新造成測試結果不可重現。
    static final GenericContainer<?> objectStorage = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.1"))
            .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
            .withEnv("RUSTFS_SECRET_KEY", "minioadmin")
            .withExposedPorts(9000)
            // 等到 S3 API 的健康檢查端點回 200 才視為啟動完成，避免 MinioConfig 建立 bucket 時連線失敗
            .waitingFor(Wait.forHttp("/health").forPort(9000).forStatusCode(200))
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
        objectStorage.start();
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

        // 物件儲存設定（RustFS，沿用 minio.* 設定鍵）
        registry.add("minio.endpoint", () -> "http://" + objectStorage.getHost() + ":" + objectStorage.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket-name", () -> "test-bucket");

        // JWT 密鑰：application.properties 已移除寫死的預設值（缺少時啟動失敗），
        // 因此測試必須自行提供。此為「僅供測試」的 512-bit Base64 值，請勿用於任何實際環境。
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
    }

    /** 測試專用 JWT 密鑰（Base64，解碼後 72 bytes = 576 bits，滿足 HS512 需求） */
    private static final String TEST_JWT_SECRET =
            "aW50ZWdyYXRpb24tdGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi0wMTIzNDU2Nzg5";

    // 注入 JdbcTemplate 用於執行原生 SQL
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 注入 StringRedisTemplate 用於每個測試後清空 Redis
    @Autowired
    private StringRedisTemplate redisTemplate;

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

        // 同步清空 Redis：訂單流水號計數器、冪等鍵、快取、速率限制計數等都存在 Redis，
        // 不清除的話前一個測試的計數會洩漏到下一個測試（也會掩蓋訂單號碼衝突之類的問題）。
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}