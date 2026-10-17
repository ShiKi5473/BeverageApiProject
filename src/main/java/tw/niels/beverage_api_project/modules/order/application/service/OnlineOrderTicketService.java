package tw.niels.beverage_api_project.modules.order.application.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Application - Service
 * ============================================================
 *
 * 線上訂單 ticket 狀態存放（新增，B-4）。
 *
 * 原本 POST /online-orders 只回傳 ticketId，卻沒有任何方式查詢結果（成功或失敗都不知道）。
 * 現在以 Redis Hash 保存 ticket 狀態，供 GET /online-orders/{ticketId} 查詢：
 * - QUEUED ：已送入 RabbitMQ 佇列，等待消費者處理
 * - CREATED：消費者已建立訂單（附 orderId、orderNumber）
 * - FAILED ：建立失敗（附失敗原因），訊息同時進入 DLQ；或發送時 broker 拒收(nack) / 無法路由(return)
 *            （修改 m-1、m-4：只在「最終」失敗時寫入，且不會覆蓋已 CREATED 的 ticket）
 *
 * Key：online-order:ticket:{ticketId}，保存 24 小時。
 * 欄位：brandId、requester（下單者 principalKey，查詢時驗證本人）、status、orderId、orderNumber、message。
 * ============================================================
 */
@Service
public class OnlineOrderTicketService {

    /** ticket 狀態 */
    public enum TicketStatus { QUEUED, CREATED, FAILED }

    /**
     * ticket 內容。
     *
     * @param ticketId    ticket ID
     * @param brandId     品牌 ID
     * @param requester   下單者 principalKey
     * @param status      ticket 狀態
     * @param orderId     訂單 ID（CREATED 後才有）
     * @param orderNumber 訂單號碼（CREATED 後才有）
     * @param message     訊息
     */
    public record Ticket(String ticketId, Long brandId, String requester, TicketStatus status,
                         Long orderId, String orderNumber, String message) {
    }

    public static final String MSG_QUEUED = "訂單處理中";
    public static final String MSG_CREATED = "訂單已建立，等待店家接單";

    private static final String KEY_PREFIX = "online-order:ticket:";
    private static final Duration TTL = Duration.ofHours(24);

    private static final String F_BRAND_ID = "brandId";
    private static final String F_REQUESTER = "requester";
    private static final String F_STATUS = "status";
    private static final String F_ORDER_ID = "orderId";
    private static final String F_ORDER_NUMBER = "orderNumber";
    private static final String F_MESSAGE = "message";

    /**
     * 新增（m-1、m-4）：原子性的「標記失敗」腳本 —— ticket 已是 CREATED 時不覆蓋。
     * KEYS[1] = ticket key；ARGV[1] = 失敗訊息；ARGV[2] = brandId（空字串代表不更新）；
     * ARGV[3] = requester（空字串代表不更新）；ARGV[4] = TTL 秒數。回傳 1 = 已寫入、0 = 已是 CREATED 未變更。
     */
    static final RedisScript<Long> MARK_FAILED_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'status') == 'CREATED' then return 0 end\n"
                    + "redis.call('HSET', KEYS[1], 'status', 'FAILED', 'message', ARGV[1])\n"
                    + "if ARGV[2] ~= '' then redis.call('HSET', KEYS[1], 'brandId', ARGV[2]) end\n"
                    + "if ARGV[3] ~= '' then redis.call('HSET', KEYS[1], 'requester', ARGV[3]) end\n"
                    + "redis.call('EXPIRE', KEYS[1], ARGV[4])\n"
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public OnlineOrderTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 建立「已排隊」的 ticket（送入佇列前呼叫，避免消費者先寫入 CREATED 又被覆蓋） */
    public void createQueued(String ticketId, Long brandId, String requester) {
        Map<String, String> fields = baseFields(brandId, requester);
        fields.put(F_STATUS, TicketStatus.QUEUED.name());
        fields.put(F_MESSAGE, MSG_QUEUED);
        write(ticketId, fields);
    }

    /** 標記訂單已建立（同時補寫 brandId / requester，ticket 過期或遺失時仍可自我修復） */
    public void markCreated(String ticketId, Long brandId, String requester, Long orderId, String orderNumber) {
        Map<String, String> fields = baseFields(brandId, requester);
        fields.put(F_STATUS, TicketStatus.CREATED.name());
        if (orderId != null) {
            fields.put(F_ORDER_ID, String.valueOf(orderId));
        }
        if (orderNumber != null) {
            fields.put(F_ORDER_NUMBER, orderNumber);
        }
        fields.put(F_MESSAGE, MSG_CREATED);
        write(ticketId, fields);
    }

    /**
     * 標記建立失敗。
     *
     * 修改（m-1、m-4）：改用 Lua 腳本原子性地「只有在尚未 CREATED 時才寫入 FAILED」。
     * FAILED 可能來自 MQ 最終失敗處理或發送端的 broker nack / return callback（在另一條執行緒非同步觸發），
     * 若訂單其實已建立（消費者已寫入 CREATED），不可再被覆蓋成 FAILED，否則前端會重新下單造成重複訂單。
     *
     * @return true 表示已寫入 FAILED；false 表示 ticket 已是 CREATED，未變更
     */
    public boolean markFailed(String ticketId, Long brandId, String requester, String message) {
        Long written = redisTemplate.execute(MARK_FAILED_SCRIPT,
                List.of(KEY_PREFIX + ticketId),
                message != null ? message : "訂單建立失敗",
                brandId != null ? String.valueOf(brandId) : "",
                requester != null ? requester : "",
                String.valueOf(TTL.toSeconds()));
        return written != null && written == 1L;
    }

    /** 讀取 ticket；不存在（或已過期）時回傳 empty */
    public Optional<Ticket> find(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> entries = ops.entries(KEY_PREFIX + ticketId);
        if (entries == null || entries.isEmpty() || entries.get(F_STATUS) == null) {
            return Optional.empty();
        }
        TicketStatus status;
        try {
            status = TicketStatus.valueOf(entries.get(F_STATUS));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new Ticket(
                ticketId,
                parseLong(entries.get(F_BRAND_ID)),
                entries.get(F_REQUESTER),
                status,
                parseLong(entries.get(F_ORDER_ID)),
                entries.get(F_ORDER_NUMBER),
                entries.get(F_MESSAGE)));
    }

    // ─── 私有輔助方法 ─────────────────────────────────────────────────────

    private Map<String, String> baseFields(Long brandId, String requester) {
        Map<String, String> fields = new HashMap<>();
        if (brandId != null) {
            fields.put(F_BRAND_ID, String.valueOf(brandId));
        }
        if (requester != null) {
            fields.put(F_REQUESTER, requester);
        }
        return fields;
    }

    private void write(String ticketId, Map<String, String> fields) {
        String key = KEY_PREFIX + ticketId;
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        ops.putAll(key, fields);
        redisTemplate.expire(key, TTL);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
