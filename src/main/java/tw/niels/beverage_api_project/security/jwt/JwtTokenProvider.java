package tw.niels.beverage_api_project.security.jwt;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import tw.niels.beverage_api_project.modules.platform.security.PlatformAdminDetails;
import tw.niels.beverage_api_project.security.AppUserDetails;

/**
 * JWT 令牌提供者類別，用於生成、驗證和解析 JWT 令牌。
 * 密鑰和過期時間是從 application.properties 讀取的，這是一個好的實踐。
 * <p>
 * 【重構】改為「解析一次取得 Claims」：Filter / STOMP 攔截器只呼叫 {@link #parseClaimsOrNull(String)} 一次，
 * 不再為了取 subject、type、brandId 各自重新驗簽解析（原本每個請求解析 4 次）。
 * </p>
 */
@Component
public class JwtTokenProvider {

    // 使用 SLF4J 記錄日誌，這是 Spring Boot 的標準做法
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // ===== Claim 名稱與 token 類型常數（集中定義，避免各處手打字串） =====
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_BRAND_ID = "brandId";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_STORE_ID = "storeId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";

    public static final String TYPE_TENANT = "TENANT";
    public static final String TYPE_GUEST = "GUEST";
    public static final String TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** 訪客 subject 前綴（GuestPrincipal.guestId 格式為 "GUEST:" + uuid） */
    public static final String GUEST_SUBJECT_PREFIX = "GUEST:";

    // 從 application.properties 或 application.yml 讀取 JWT 密鑰
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // 從 application.properties 讀取 JWT 過期時間（毫秒）
    // 【修正】改為 long：int 最多約 24.8 天，設定較長效期時會溢位
    @Value("${app.jwt.expiration-in-ms}")
    private long jwtExpirationInMs;

    // JWT 簽名用的密鑰物件
    private SecretKey key;

    /**
     * 在類別實例化後，依賴注入完成後，立即執行此方法。
     * 它負責將從設定檔讀取的 Base64 編碼密鑰轉換為 SecretKey 物件。
     */
    @PostConstruct
    public void init() {
        // 使用 Base64 解碼器將密鑰字串轉換為位元組陣列
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        // 使用 jjwt 函式庫建立 HMAC-SHA 的 SecretKey 物件
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 根據 Spring Security 的 Authentication 物件生成 JWT 令牌。
     * <p>
     * 【修改】TENANT token 直接帶入 userId、storeId、role，
     * 讓 JwtAuthenticationFilter 可以「只靠 claims」建立身分，不必每個請求查 DB。
     * 停權或改權限時改由 TokenRevocationService 讓舊 token 失效。
     * </p>
     *
     * @param authentication 包含已認證用戶資訊的物件
     * @return 創建的 JWT 令牌字串
     */
    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationInMs);

        // 使用 Jwts.builder() 創建令牌
        var tokenBuilder = Jwts.builder()
                .subject(username)
                .issuedAt(currentDate)
                .expiration(expireDate);

        // 檢查 Principal 的類型
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserDetails userDetails) {
            // 品牌員工／會員：存入 brandId、userId、storeId（可為 null）、role
            tokenBuilder.claim(CLAIM_TYPE, TYPE_TENANT);
            tokenBuilder.claim(CLAIM_BRAND_ID, userDetails.brandId());
            tokenBuilder.claim(CLAIM_USER_ID, userDetails.userId());
            if (userDetails.storeId() != null) {
                tokenBuilder.claim(CLAIM_STORE_ID, userDetails.storeId());
            }
            String role = firstAuthority(userDetails);
            if (role != null) {
                tokenBuilder.claim(CLAIM_ROLE, role);
            }
        } else if (principal instanceof PlatformAdminDetails) {
            // 平台管理員：不存 brandId（仍由 Filter 查 DB 載入，平台管理員請求量很小）
            tokenBuilder.claim(CLAIM_TYPE, TYPE_PLATFORM_ADMIN);
        }

        return tokenBuilder
                .signWith(key) // 使用密鑰簽名
                .compact(); // 壓縮成最終的令牌字串
    }

    /**
     * 生成訪客專用的 JWT 令牌。
     * <p>
     * 【修改】訪客 token 必須帶 brandId（線上點餐需依品牌隔離商品與分店），
     * claims：type=GUEST、brandId、name；subject 為 "GUEST:" + uuid。
     * </p>
     *
     * @param displayName 訪客顯示名稱
     * @param brandId     訪客點餐的品牌 ID
     * @return 訪客 JWT 令牌
     */
    public String generateGuestToken(String displayName, Long brandId) {
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationInMs);

        // 產生一個隨機的 ID 作為 Subject，避免重複
        String guestId = GUEST_SUBJECT_PREFIX + UUID.randomUUID();

        return Jwts.builder()
                .subject(guestId)
                .issuedAt(currentDate)
                .expiration(expireDate)
                .claim(CLAIM_TYPE, TYPE_GUEST)       // 標記為訪客類型
                .claim(CLAIM_BRAND_ID, brandId)      // 訪客所屬品牌
                .claim(CLAIM_NAME, displayName)      // 存入顯示名稱
                .signWith(key)
                .compact();
    }

    /**
     * 驗證簽章並解析 Claims（只解析一次）。
     *
     * @param token JWT 字串
     * @return Claims
     * @throws JwtException             簽章錯誤、過期、格式錯誤等（含 SignatureException）
     * @throws IllegalArgumentException token 為空字串
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key) // 使用密鑰驗證簽名
                .build()
                .parseSignedClaims(token) // 解析已簽名的令牌
                .getPayload(); // 取得負載（Payload），即 Claims 物件
    }

    /**
     * 驗證並解析 token；無效時回傳 null 而非拋出例外。
     * <p>
     * 【修正】原本 validateToken 沒有捕捉 SignatureException，竄改簽章的 token 會讓例外穿出 Filter 變成 HTTP 500。
     * 改為捕捉 jjwt 的共同父類別 {@link JwtException}（涵蓋 Signature / Malformed / Expired / Unsupported）
     * 與 {@link IllegalArgumentException}。無效 token 屬於使用者端問題，只記 DEBUG，避免 log 洪水。
     * </p>
     *
     * @param token JWT 字串
     * @return 有效時回傳 Claims，否則 null
     */
    public Claims parseClaimsOrNull(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException ex) {
            logger.debug("JWT token 已過期");
        } catch (JwtException ex) {
            logger.debug("無效的 JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.debug("JWT 字串為空");
        }
        return null;
    }

    /**
     * 驗證 JWT 令牌的有效性。
     *
     * @param token 待驗證的 JWT 令牌字串
     * @return 如果令牌有效則返回 true，否則返回 false
     */
    public boolean validateToken(String token) {
        // 【修正】統一透過 parseClaimsOrNull 捕捉 JwtException | IllegalArgumentException
        return parseClaimsOrNull(token) != null;
    }

    /**
     * 從 Claims 讀取 Long 型別的 claim。
     * <p>
     * JSON 數字解析後可能是 Integer（小 ID，如種子資料）或 Long（TSID），
     * 也相容以字串存放的情況，統一轉為 Long。
     * </p>
     */
    public static Long getLongClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取 UserDetails 的第一個權限作為 role（員工僅有一個角色）。
     */
    private static String firstAuthority(AppUserDetails userDetails) {
        if (userDetails.getAuthorities() == null || userDetails.getAuthorities().isEmpty()) {
            return null;
        }
        GrantedAuthority authority = userDetails.getAuthorities().iterator().next();
        return authority.getAuthority();
    }
}
