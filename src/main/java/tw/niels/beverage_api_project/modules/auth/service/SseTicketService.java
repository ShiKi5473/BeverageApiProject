package tw.niels.beverage_api_project.modules.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tw.niels.beverage_api_project.security.AppUserDetails;

/**
 * 產生並驗證一次性 SSE 連線票券。
 * 用來取代在 URL query parameter 中直接傳遞 JWT 的做法，
 * 避免 JWT 被記錄在 server log、proxy log 或瀏覽器歷史中。
 *
 * 流程：
 * 1. 前端以 Authorization header 呼叫 POST /api/v1/auth/sse-ticket
 * 2. 後端產生一次性 ticket，存入 Redis（30 秒 TTL）
 * 3. 前端以 EventSource(/api/v1/kds/stream?ticket=xxx) 建立連線
 * 4. 後端驗證 ticket 後立即從 Redis 刪除（single-use）
 *
 * 【修正】原本 ticket 只存 username，Filter 用 loadUserByUsername 載入時沒有 BrandContextHolder
 * → 必定 UsernameNotFoundException → SSE 永遠 401。
 * 改為把建立 ticket 當下的身分（userId、brandId、storeId、role、username）以 JSON 存入 Redis，
 * Filter 直接由內容重建 AppUserDetails，不需查 DB、也不依賴 BrandContextHolder。
 */
@Service
public class SseTicketService {

    private static final Logger logger = LoggerFactory.getLogger(SseTicketService.class);
    private static final String REDIS_KEY_PREFIX = "sse-ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SseTicketService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Ticket 在 Redis 中保存的身分資料（JSON）。
     */
    public record SseTicketPayload(Long userId, Long brandId, Long storeId, String role, String username) {

        /** 由 payload 重建 AppUserDetails（不查 DB） */
        public AppUserDetails toUserDetails() {
            return AppUserDetails.fromClaims(userId, brandId, storeId, username, role);
        }
    }

    /**
     * 為指定使用者產生一次性 SSE ticket。
     *
     * @param user 目前已認證的員工／會員身分
     * @return 隨機產生的 ticket 字串
     */
    public String createTicket(AppUserDetails user) {
        String ticket = UUID.randomUUID().toString();
        String redisKey = REDIS_KEY_PREFIX + ticket;
        SseTicketPayload payload = new SseTicketPayload(
                user.userId(), user.brandId(), user.storeId(), user.primaryRole(), user.getUsername());
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(payload), TICKET_TTL);
        } catch (JsonProcessingException e) {
            // 純 record 序列化理論上不會失敗；若失敗視為系統錯誤
            throw new IllegalStateException("無法建立 SSE ticket", e);
        }
        logger.debug("已為使用者 {} 建立 SSE ticket（TTL={}s）", user.userId(), TICKET_TTL.getSeconds());
        return ticket;
    }

    /**
     * 驗證並消費一次性 SSE ticket。
     * 驗證成功後會立即從 Redis 刪除，確保 ticket 只能使用一次。
     *
     * @param ticket 前端傳入的 ticket
     * @return 對應的身分資料；若 ticket 無效、已過期或內容損毀則回傳 empty
     */
    public Optional<SseTicketPayload> validateAndConsumeTicket(String ticket) {
        String redisKey = REDIS_KEY_PREFIX + ticket;
        // getAndDelete 為原子操作：取出並刪除，確保 single-use
        String json = redisTemplate.opsForValue().getAndDelete(redisKey);
        if (json == null) {
            // 改為 DEBUG 且不輸出 ticket 本身：前端重連時常見，避免 log 洪水
            logger.debug("SSE ticket 驗證失敗（無效或已過期）");
            return Optional.empty();
        }
        try {
            SseTicketPayload payload = objectMapper.readValue(json, SseTicketPayload.class);
            if (payload.userId() == null || payload.brandId() == null) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (JsonProcessingException e) {
            // 舊格式（只存 username）或內容損毀：視為無效 ticket
            logger.debug("SSE ticket 內容格式錯誤: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
