package tw.niels.beverage_api_project.modules.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderNumberService 單元測試（新增，m-2 Redis 計數器遺失時以資料庫播種）。
 *
 * - 計數器存在：只走 Redis 快速路徑，不查資料庫
 * - 計數器不存在：以資料庫中該分店當日最大的流水號播種，接續編號（不會從 1 重新開始而撞號）
 * - 流水號超過 4 位數也能正確解析
 * - 併發播種時（以模擬 Lua 腳本語意的記憶體實作驗證）不會產生重複流水號
 * - 營業日依注入的 Clock（Asia/Taipei）決定
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderNumberService 訂單流水號（Redis + 資料庫播種）")
class OrderNumberServiceTest {

    private static final Long STORE_ID = 10L;
    /** 2026-10-16T16:30Z = 台北 2026-10-17 00:30（UTC 仍是前一天，用來驗證使用營業時區） */
    private static final Instant NOW = Instant.parse("2026-10-16T16:30:00Z");
    private static final String DATE = "20261017";
    private static final String KEY = "order_counter:store_id:10:date:" + DATE;
    private static final String TTL_SECONDS = String.valueOf(OrderNumberService.COUNTER_TTL.toSeconds());

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OrderJpaRepository orderJpaRepository;

    private OrderNumberService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Taipei"));
        service = new OrderNumberService(redisTemplate, clock, orderJpaRepository);
    }

    @Test
    @DisplayName("計數器存在：直接 INCR，不查資料庫")
    void counterExists_shouldUseRedisOnly() {
        when(redisTemplate.execute(same(OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT), eq(List.of(KEY)), eq(TTL_SECONDS)))
                .thenReturn(8L);

        assertThat(service.nextOrderNumber(STORE_ID)).isEqualTo("10-20261017-0008");
        verify(orderJpaRepository, never()).findTopOrderNumbersByStoreIdAndPrefix(any(), any(), any());
    }

    @Test
    @DisplayName("計數器遺失（Redis 重啟 / FLUSHALL）：以資料庫當日最大流水號播種，接續為 0043")
    void counterMissing_shouldSeedFromDatabaseMax() {
        when(redisTemplate.execute(same(OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT), eq(List.of(KEY)), eq(TTL_SECONDS)))
                .thenReturn(OrderNumberService.COUNTER_MISSING);
        when(orderJpaRepository.findTopOrderNumbersByStoreIdAndPrefix(eq(STORE_ID), eq("10-20261017-%"), any(Pageable.class)))
                .thenReturn(List.of("10-20261017-0042", "10-20261017-0041"));
        when(redisTemplate.execute(same(OrderNumberService.SEED_AND_INCREMENT_SCRIPT), eq(List.of(KEY)),
                eq("42"), eq(TTL_SECONDS)))
                .thenReturn(43L);

        assertThat(service.nextOrderNumber(STORE_ID)).isEqualTo("10-20261017-0043");
    }

    @Test
    @DisplayName("計數器遺失且當日尚無訂單：種子為 0，從 0001 開始")
    void counterMissingAndNoOrders_shouldStartFromOne() {
        when(redisTemplate.execute(same(OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT), eq(List.of(KEY)), eq(TTL_SECONDS)))
                .thenReturn(OrderNumberService.COUNTER_MISSING);
        when(orderJpaRepository.findTopOrderNumbersByStoreIdAndPrefix(eq(STORE_ID), eq("10-20261017-%"), any(Pageable.class)))
                .thenReturn(List.of());
        when(redisTemplate.execute(same(OrderNumberService.SEED_AND_INCREMENT_SCRIPT), eq(List.of(KEY)),
                eq("0"), eq(TTL_SECONDS)))
                .thenReturn(1L);

        assertThat(service.nextOrderNumber(STORE_ID)).isEqualTo("10-20261017-0001");
    }

    @Test
    @DisplayName("流水號超過 4 位數（10000）與無法解析的號碼：取可解析的最大值作為種子")
    void counterMissing_shouldParseFiveDigitSequenceAndSkipGarbage() {
        when(redisTemplate.execute(same(OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT), eq(List.of(KEY)), eq(TTL_SECONDS)))
                .thenReturn(OrderNumberService.COUNTER_MISSING);
        when(orderJpaRepository.findTopOrderNumbersByStoreIdAndPrefix(eq(STORE_ID), eq("10-20261017-%"), any(Pageable.class)))
                .thenReturn(List.of("10-20261017-X001", "10-20261017-10000", "10-20261017-9999"));
        when(redisTemplate.execute(same(OrderNumberService.SEED_AND_INCREMENT_SCRIPT), eq(List.of(KEY)),
                eq("10000"), eq(TTL_SECONDS)))
                .thenReturn(10001L);

        assertThat(service.nextOrderNumber(STORE_ID)).isEqualTo("10-20261017-10001");
    }

    @Test
    @DisplayName("Redis 無回應（null）：拋出例外讓交易回滾，不產生非法號碼")
    void redisReturnsNull_shouldThrow() {
        when(redisTemplate.execute(same(OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT), eq(List.of(KEY)), eq(TTL_SECONDS)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.nextOrderNumber(STORE_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("併發播種：多個執行緒同時發現計數器遺失，取得的流水號仍不重複且接續資料庫最大值")
    void concurrentSeeding_shouldNeverProduceDuplicates() throws Exception {
        // 以記憶體 + 同步鎖模擬兩支 Lua 腳本的原子語意（Redis 單執行緒執行腳本）
        FakeRedisCounter fakeRedis = new FakeRedisCounter();
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
                .thenAnswer(invocation -> fakeRedis.run(invocation.getArgument(0), invocation.getArgument(1),
                        new Object[]{invocation.getArgument(2)}));
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString(), anyString()))
                .thenAnswer(invocation -> fakeRedis.run(invocation.getArgument(0), invocation.getArgument(1),
                        new Object[]{invocation.getArgument(2), invocation.getArgument(3)}));

        // 所有執行緒都先看到「計數器不存在」並查詢資料庫（最大流水號 5），之後才各自執行播種腳本
        int threads = 8;
        CountDownLatch allQueriedDatabase = new CountDownLatch(threads);
        when(orderJpaRepository.findTopOrderNumbersByStoreIdAndPrefix(eq(STORE_ID), eq("10-20261017-%"), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    allQueriedDatabase.countDown();
                    allQueriedDatabase.await(5, TimeUnit.SECONDS);
                    return List.of("10-20261017-0005");
                });

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> task = () -> service.nextOrderNumber(STORE_ID);
                futures.add(executor.submit(task));
            }
            List<String> numbers = new ArrayList<>();
            for (Future<String> future : futures) {
                numbers.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(numbers).doesNotHaveDuplicates();
            assertThat(numbers).containsExactlyInAnyOrder(
                    "10-20261017-0006", "10-20261017-0007", "10-20261017-0008", "10-20261017-0009",
                    "10-20261017-0010", "10-20261017-0011", "10-20261017-0012", "10-20261017-0013");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 模擬 OrderNumberService 兩支 Lua 腳本語意的記憶體計數器（synchronized = Redis 原子執行腳本）。
     */
    private static final class FakeRedisCounter {
        private final Map<String, Long> values = Collections.synchronizedMap(new HashMap<>());

        synchronized Long run(RedisScript<?> script, List<String> keys, Object[] args) {
            String key = keys.get(0);
            if (script == OrderNumberService.INCREMENT_IF_EXISTS_SCRIPT) {
                if (!values.containsKey(key)) {
                    return OrderNumberService.COUNTER_MISSING;
                }
                long next = values.get(key) + 1;
                values.put(key, next);
                return next;
            }
            if (script == OrderNumberService.SEED_AND_INCREMENT_SCRIPT) {
                long seed = Long.parseLong((String) args[0]);
                values.putIfAbsent(key, seed);           // SET NX
                long next = values.get(key) + 1;          // INCR
                if (next <= seed) {                      // 校正為資料庫最大值 + 1
                    next = seed + 1;
                }
                values.put(key, next);
                return next;
            }
            throw new IllegalArgumentException("unexpected script");
        }
    }
}
