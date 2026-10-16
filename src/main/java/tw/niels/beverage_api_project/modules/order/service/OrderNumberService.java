package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 訂單號碼服務。
 *
 * 修改說明（P0-3 訂單號碼衝突、B-7 時區）：
 * - 新增 nextOrderNumber()：產生 {storeId}-{yyyyMMdd}-{seq:04d}，
 *   符合 V1 的唯一鍵 uk_orders_brand_order_number(brand_id, order_number)
 *   （原本 Handler 產生的 "A%03d" 每店每日重置，第二家分店或隔日就會撞號）。
 * - 「今天」改用注入的 Clock（營業時區，預設 Asia/Taipei），不再依賴 JVM 預設時區
 *   （Docker 預設 UTC，流水號會在台灣早上 8 點才重置）。
 */
@Service
public class OrderNumberService {

    private final StringRedisTemplate redisTemplate;
    // 新增：營業時區時鐘（TimeConfig 提供）
    private final Clock clock;
    // 定義日期格式，用於產生 Redis Key 與訂單號碼
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public OrderNumberService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    /**
     * 產生下一個訂單號碼，格式：{storeId}-{yyyyMMdd}-{seq:04d}（例：1-20251105-0007）。
     * 前端以最後一段作為取餐號碼。
     *
     * @param storeId 店家 ID
     * @return 品牌內唯一的訂單號碼
     */
    public String nextOrderNumber(Long storeId) {
        // 日期與 Redis Key 使用同一個「營業日」，避免跨日瞬間兩者不一致
        String dateString = LocalDate.now(clock).format(DATE_FORMATTER);
        long sequence = nextSequence(storeId, dateString);
        return String.format("%d-%s-%04d", storeId, dateString, sequence);
    }

    /**
     * 獲取指定店家當天的下一個訂單序號
     *
     * @param storeId 店家 ID
     * @return 當天的訂單流水號 (例如 1, 2, 3...)
     */
    public long getNextStoreDailySequence(Long storeId) {
        // 修改：使用營業時區的日期
        return nextSequence(storeId, LocalDate.now(clock).format(DATE_FORMATTER));
    }

    private long nextSequence(Long storeId, String dateString) {
        // 1. 產生 Redis Key，格式為：order_counter:store_id:{storeId}:date:{yyyyMMdd}
        String redisKey = String.format("order_counter:store_id:%d:date:%s", storeId, dateString);

        // 2. 使用 INCR 指令（Key 不存在時 Redis 自動建立並設為 1）
        Long sequence = redisTemplate.opsForValue().increment(redisKey);

        // 3. 第一次建立時設定 48 小時 TTL，自動清除舊的計數器
        if (sequence != null && sequence == 1) {
            redisTemplate.expire(redisKey, 48, TimeUnit.HOURS);
        }

        if (sequence == null) {
            // 修改：原本回傳 -1 會產生非法的訂單號碼，改為直接拋出例外讓交易回滾
            throw new IllegalStateException("無法從 Redis 取得訂單流水號 (storeId=" + storeId + ")");
        }
        return sequence;
    }
}
