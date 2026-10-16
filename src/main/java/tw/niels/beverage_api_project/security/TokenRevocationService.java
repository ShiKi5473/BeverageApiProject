package tw.niels.beverage_api_project.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 撤銷服務。
 * <p>
 * 因 JWT Filter 改為直接由 token claims 建立身分（不再每次查 DB），
 * 停權或變更權限時需呼叫 {@link #revokeUser(Long)}，讓該使用者在此時間點之前簽發的 token 立即失效。
 * 紀錄保存時間等於 token 有效期，過期後自動清除。
 * </p>
 */
@Service
public class TokenRevocationService {

    private static final String KEY_PREFIX = "token-revoked:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public TokenRevocationService(StringRedisTemplate redisTemplate,
                                  @Value("${app.jwt.expiration-in-ms}") long jwtExpirationInMs) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(jwtExpirationInMs);
    }

    /**
     * 撤銷指定使用者目前所有已簽發的 token（記錄撤銷時間點）。
     */
    public void revokeUser(Long userId) {
        if (userId == null) return;
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, String.valueOf(Instant.now().toEpochMilli()), ttl);
    }

    /**
     * 判斷某 token 是否已被撤銷。
     *
     * @param userId   token 所屬使用者
     * @param issuedAt token 簽發時間
     * @return true 表示簽發時間早於撤銷時間點，應拒絕
     */
    public boolean isRevoked(Long userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) return false;
        String revokedAt = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (revokedAt == null) return false;
        // JWT iat 精度為秒，撤銷時間點以毫秒記錄；簽發時間不晚於撤銷時間即視為已撤銷
        return issuedAt.toEpochMilli() <= Long.parseLong(revokedAt);
    }
}
