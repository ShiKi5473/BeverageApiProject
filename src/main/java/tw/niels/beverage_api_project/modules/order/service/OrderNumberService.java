package tw.niels.beverage_api_project.modules.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 訂單號碼服務。
 *
 * 修改說明（P0-3 訂單號碼衝突、B-7 時區）：
 * - 新增 nextOrderNumber()：產生 {storeId}-{yyyyMMdd}-{seq:04d}，
 *   符合 V1 的唯一鍵 uk_orders_brand_order_number(brand_id, order_number)
 *   （原本 Handler 產生的 "A%03d" 每店每日重置，第二家分店或隔日就會撞號）。
 * - 「今天」改用注入的 Clock（營業時區，預設 Asia/Taipei），不再依賴 JVM 預設時區
 *   （Docker 預設 UTC，流水號會在台灣早上 8 點才重置）。
 *
 * 修改說明（m-2 Redis 計數器遺失）：
 * - 原本流水號只存在 Redis（order_counter:store_id:X:date:Y），Key 遺失（Redis 重啟未持久化、測試 FLUSHALL）時
 *   INCR 會從 1 重新開始，當天之後的每一張訂單都撞到唯一鍵（POS 409 / 線上訂單 FAILED）。
 * - 現在分兩步：
 *   1. 快速路徑（Lua，原子）：Key 存在才 INCR（順便補上遺失的 TTL）；Key 不存在回傳 -1，不會建立從 1 開始的計數器
 *   2. 播種路徑：從資料庫查出該分店該營業日最大的流水號，再以 Lua 原子地「SET NX 種子值 + INCR」，
 *      若計數器已被其他實例先建立但值比資料庫小，則往上校正為「資料庫最大值 + 1」
 *   併發呼叫者即使同時走到播種路徑，也只有一個 SET NX 會成功，其餘都是對同一個 Key INCR，不會拿到重複的流水號。
 * - 只有 Key 不存在時才查資料庫，正常情況下每張訂單仍只有一次 Redis 呼叫。
 */
@Service
public class OrderNumberService {

    private static final Logger logger = LoggerFactory.getLogger(OrderNumberService.class);

    private final StringRedisTemplate redisTemplate;
    // 新增：營業時區時鐘（TimeConfig 提供）
    private final Clock clock;
    // 新增（m-2）：Redis 計數器遺失時，從資料庫查詢當日最大流水號
    private final OrderJpaRepository orderJpaRepository;
    // 定義日期格式，用於產生 Redis Key 與訂單號碼
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 計數器保存 48 小時（跨日後舊的計數器自動清除） */
    static final Duration COUNTER_TTL = Duration.ofHours(48);

    /** 新增：播種時從資料庫取前幾筆（防禦性：略過無法解析的號碼後仍能找到最大值） */
    private static final int SEED_LOOKUP_SIZE = 5;

    /** 快速路徑回傳值：計數器不存在，需要從資料庫播種 */
    static final long COUNTER_MISSING = -1L;

    /**
     * 新增（m-2）：快速路徑腳本 —— Key 存在才 INCR；沒有 TTL（舊版 INCR 與 EXPIRE 非原子留下的）就補上。
     * KEYS[1] = 計數器 Key；ARGV[1] = TTL 秒數。回傳新的流水號，Key 不存在時回傳 -1。
     */
    static final RedisScript<Long> INCREMENT_IF_EXISTS_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then\n"
                    + "  local seq = redis.call('INCR', KEYS[1])\n"
                    + "  if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
                    + "  return seq\n"
                    + "end\n"
                    + "return -1",
            Long.class);

    /**
     * 新增（m-2）：播種腳本 —— 原子地以資料庫最大流水號初始化計數器後 INCR。
     * KEYS[1] = 計數器 Key；ARGV[1] = 種子值（資料庫當日最大流水號，沒有訂單時為 0）；ARGV[2] = TTL 秒數。
     * - SET NX：只有第一個呼叫者會設定種子，其他併發呼叫者直接 INCR 同一個 Key
     * - INCR 結果若仍 ≤ 種子（計數器已存在但值比資料庫小，例如舊版程式在遺失後從 1 重新計數），校正為種子 + 1
     */
    static final RedisScript<Long> SEED_AND_INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local seed = tonumber(ARGV[1])\n"
                    + "redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])\n"
                    + "local seq = redis.call('INCR', KEYS[1])\n"
                    + "if seq <= seed then\n"
                    + "  seq = seed + 1\n"
                    + "  redis.call('SET', KEYS[1], tostring(seq), 'EX', ARGV[2])\n"
                    + "end\n"
                    + "return seq",
            Long.class);

    public OrderNumberService(StringRedisTemplate redisTemplate, Clock clock, OrderJpaRepository orderJpaRepository) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.orderJpaRepository = orderJpaRepository;
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

    // 移除：getNextStoreDailySequence()（只回傳流水號，Handler 以此組出違反唯一鍵的 "A%03d"），
    // 所有 Handler 已改用 nextOrderNumber()。

    private long nextSequence(Long storeId, String dateString) {
        // 1. 產生 Redis Key，格式為：order_counter:store_id:{storeId}:date:{yyyyMMdd}
        String redisKey = String.format("order_counter:store_id:%d:date:%s", storeId, dateString);
        List<String> keys = List.of(redisKey);
        String ttlSeconds = String.valueOf(COUNTER_TTL.toSeconds());

        // 2. 快速路徑：計數器存在就直接 INCR（修改：不再用裸 INCR，避免 Key 遺失時從 1 重新開始）
        Long sequence = redisTemplate.execute(INCREMENT_IF_EXISTS_SCRIPT, keys, ttlSeconds);

        // 3. 計數器不存在：以資料庫中該分店當日最大的流水號播種後再 INCR（原子操作）
        if (sequence != null && sequence == COUNTER_MISSING) {
            long seed = findMaxSequenceInDatabase(storeId, dateString);
            if (seed > 0) {
                logger.warn("訂單流水號計數器不存在，從資料庫最大流水號 {} 接續 (storeId={}, date={})",
                        seed, storeId, dateString);
            }
            sequence = redisTemplate.execute(SEED_AND_INCREMENT_SCRIPT, keys, String.valueOf(seed), ttlSeconds);
        }

        if (sequence == null || sequence <= 0) {
            // 修改：原本回傳 -1 會產生非法的訂單號碼，改為直接拋出例外讓交易回滾
            throw new IllegalStateException("無法從 Redis 取得訂單流水號 (storeId=" + storeId + ")");
        }
        return sequence;
    }

    /**
     * 新增（m-2）：查詢資料庫中該分店該營業日已使用的最大流水號（沒有訂單時回傳 0）。
     * 訂單號碼前綴為 "{storeId}-{yyyyMMdd}-"，最後一段為流水號；無法解析的號碼（理論上不會出現）略過。
     */
    private long findMaxSequenceInDatabase(Long storeId, String dateString) {
        String prefix = storeId + "-" + dateString + "-";
        List<String> orderNumbers = orderJpaRepository.findTopOrderNumbersByStoreIdAndPrefix(
                storeId, prefix + "%", PageRequest.of(0, SEED_LOOKUP_SIZE));
        long max = 0L;
        if (orderNumbers == null) {
            return max;
        }
        for (String orderNumber : orderNumbers) {
            if (orderNumber == null || !orderNumber.startsWith(prefix)) {
                continue;
            }
            String suffix = orderNumber.substring(prefix.length());
            if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
                continue;
            }
            try {
                max = Math.max(max, Long.parseLong(suffix));
            } catch (NumberFormatException e) {
                // 位數過長無法轉成 long：略過
            }
        }
        return max;
    }
}
