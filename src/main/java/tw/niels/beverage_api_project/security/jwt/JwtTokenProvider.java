package tw.niels.beverage_api_project.security.jwt;

import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import tw.niels.beverage_api_project.security.AppUserDetails;

/**
 * JWT 令牌提供者類別，用於生成、驗證和解析 JWT 令牌。
 * 密鑰和過期時間是從 application.properties 讀取的，這是一個好的實踐。
 */
@Component
public class JwtTokenProvider {

    // 使用 SLF4J 記錄日誌，這是 Spring Boot 的標準做法
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // 從 application.properties 或 application.yml 讀取 JWT 密鑰
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // 從 application.properties 或 application.yml 讀取 JWT 過期時間（毫秒）
    @Value("${app.jwt.expiration-in-ms}")
    private int jwtExpirationInMs;

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
     *
     * @param authentication 包含已認證用戶資訊的物件
     * @return 創建的 JWT 令牌字串
     */
    public String generateToken(Authentication authentication) {
        // 取得用戶名稱作為 JWT 的主體（Subject）
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        Long brandId = userDetails.getBrandId();

        Date currentDate = new Date();
        // 計算令牌的過期時間
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationInMs);

        // 使用 Jwts.builder() 創建令牌
        return Jwts.builder()
                .subject(username) // 設定主體
                .claim("brandId", brandId) // 將 brandId 作為一個 "claim" 加入 Token
                .issuedAt(currentDate) // 設定簽發時間
                .expiration(expireDate) // 設定過期時間
                .signWith(key) // 使用密鑰簽名
                .compact(); // 壓縮成最終的令牌字串
    }

    /**
     * 從 JWT 令牌中提取使用者名稱。
     *
     * @param token JWT 令牌字串
     * @return 令牌中包含的使用者名稱
     */
    public String getUsernameFromJWT(String token) {
        // 建立 JWT 解析器並使用密鑰進行驗證
        Claims claims = Jwts.parser()
                .verifyWith(key) // 使用密鑰驗證簽名
                .build()
                .parseSignedClaims(token) // 解析已簽名的令牌
                .getPayload(); // 取得負載（Payload），即 Claims 物件

        return claims.getSubject(); // 返回主體（使用者名稱）
    }

    /**
     * 專門用來從 Token 中解析出 brandId。
     */
    public Long getBrandIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("brandId", Long.class);
    }

    /**
     * 驗證 JWT 令牌的有效性。
     *
     * @param token 待驗證的 JWT 令牌字串
     * @return 如果令牌有效則返回 true，否則返回 false
     */
    public boolean validateToken(String token) {
        try {
            // 嘗試解析令牌，如果成功則表示令牌有效
            Jwts.parser().verifyWith(key).build().parse(token);
            return true;
        } catch (MalformedJwtException ex) {
            // 如果令牌格式不正確
            logger.error("無效的 JWT token");
        } catch (ExpiredJwtException ex) {
            // 如果令牌已過期
            logger.error("JWT token 已過期");
        } catch (UnsupportedJwtException ex) {
            // 如果令牌使用了不支援的簽名演算法
            logger.error("不支援的 JWT token");
        } catch (IllegalArgumentException ex) {
            // 如果 JWT claims 字串為空或 null
            logger.error("JWT claims 字串為空");
        }
        return false;
    }
}