package tw.niels.beverage_api_project.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * 以 Redis 固定視窗計數實作的簡易速率限制。
 * <p>
 * 用於登入、訪客登入、線上下單等容易被濫用的端點。
 * 超過上限時拋出 HTTP 429（Too Many Requests）。
 * </p>
 */
@Service
public class RateLimitService {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 檢查並累加計數，超過上限即拋出 429。
     *
     * @param key    限流鍵（例如 "guest-login:" + IP）
     * @param limit  視窗內允許的最大次數
     * @param window 視窗長度
     */
    public void checkOrThrow(String key, int limit, Duration window) {
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            // 第一次計數時設定視窗過期時間
            redisTemplate.expire(redisKey, window);
        }
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "請求過於頻繁，請稍後再試");
        }
    }
}
