package tw.niels.beverage_api_project.modules.auth.service;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 產生並驗證一次性 SSE 連線票券。
 * 用來取代在 URL query parameter 中直接傳遞 JWT 的做法，
 * 避免 JWT 被記錄在 server log、proxy log 或瀏覽器歷史中。
 *
 * 流程：
 * 1. 前端以 Authorization header 呼叫 POST /api/v1/auth/sse-ticket
 * 2. 後端產生一次性 ticket，存入 Redis（30 秒 TTL）
 * 3. 前端以 EventSource(/api/v1/kds/stream?ticket=xxx) 建立 SSE 連線
 * 4. 後端驗證 ticket 後立即從 Redis 刪除（single-use）
 */
@Service
public class SseTicketService {

    private static final Logger logger = LoggerFactory.getLogger(SseTicketService.class);
    private static final String REDIS_KEY_PREFIX = "sse-ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public SseTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 為指定使用者產生一次性 SSE ticket。
     *
     * @param username 使用者名稱（作為 ticket 對應的身份標識）
     * @return 隨機產生的 ticket 字串
     */
    public String createTicket(String username) {
        String ticket = UUID.randomUUID().toString();
        String redisKey = REDIS_KEY_PREFIX + ticket;
        redisTemplate.opsForValue().set(redisKey, username, TICKET_TTL);
        logger.debug("已為使用者 {} 建立 SSE ticket（TTL={}s）", username, TICKET_TTL.getSeconds());
        return ticket;
    }

    /**
     * 驗證並消費一次性 SSE ticket。
     * 驗證成功後會立即從 Redis 刪除，確保 ticket 只能使用一次。
     *
     * @param ticket 前端傳入的 ticket
     * @return 對應的使用者名稱，若 ticket 無效或已過期則回傳 null
     */
    public String validateAndConsumeTicket(String ticket) {
        String redisKey = REDIS_KEY_PREFIX + ticket;
        // getAndDelete 為原子操作：取出並刪除，確保 single-use
        String username = redisTemplate.opsForValue().getAndDelete(redisKey);
        if (username == null) {
            logger.warn("SSE ticket 驗證失敗（無效或已過期）: {}", ticket);
        }
        return username;
    }
}
