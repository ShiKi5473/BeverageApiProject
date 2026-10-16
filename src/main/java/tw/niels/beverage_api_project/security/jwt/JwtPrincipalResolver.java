package tw.niels.beverage_api_project.security.jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.GuestPrincipal;
import tw.niels.beverage_api_project.security.TokenRevocationService;

/**
 * 【新增】由 JWT 建立 Spring Security 認證物件的共用元件。
 * <p>
 * 供 {@link JwtAuthenticationFilter}（HTTP）與 {@link JwtAuthChannelInterceptor}（STOMP）共用，
 * 確保兩條路徑的驗證規則一致：
 * <ul>
 *   <li>token 只解析一次（{@link JwtTokenProvider#parseClaimsOrNull}）</li>
 *   <li>TENANT：直接由 claims 建立 {@link AppUserDetails}（不查 DB），並檢查 {@link TokenRevocationService} 撤銷紀錄</li>
 *   <li>GUEST：建立 {@link GuestPrincipal}（ROLE_GUEST），必須帶 brandId</li>
 *   <li>PLATFORM_ADMIN：仍查 DB 載入（僅 HTTP 允許，請求量小）</li>
 * </ul>
 * 任何驗證失敗都回傳 null（不拋例外），由呼叫端決定要匿名放行或拒絕。
 * </p>
 */
@Component
public class JwtPrincipalResolver {

    private static final Logger logger = LoggerFactory.getLogger(JwtPrincipalResolver.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService platformAdminDetailsService;
    private final TokenRevocationService tokenRevocationService;

    public JwtPrincipalResolver(JwtTokenProvider jwtTokenProvider,
                                @Qualifier("platformAdminDetailsService") UserDetailsService platformAdminDetailsService,
                                TokenRevocationService tokenRevocationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.platformAdminDetailsService = platformAdminDetailsService;
        this.tokenRevocationService = tokenRevocationService;
    }

    /**
     * 解析 token 並建立認證物件。
     *
     * @param token              JWT 字串（不含 "Bearer " 前綴）
     * @param allowPlatformAdmin 是否接受平台管理員 token（STOMP 不接受）
     * @return 已認證的 Authentication；token 無效、已撤銷或類型不允許時回傳 null
     */
    public UsernamePasswordAuthenticationToken resolve(String token, boolean allowPlatformAdmin) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        // 只解析一次；簽章錯誤、過期、格式錯誤一律回傳 null（不再讓 SignatureException 變成 500）
        Claims claims = jwtTokenProvider.parseClaimsOrNull(token);
        if (claims == null) {
            return null;
        }

        String type = claims.get(JwtTokenProvider.CLAIM_TYPE, String.class);
        if (JwtTokenProvider.TYPE_TENANT.equals(type)) {
            return resolveTenant(claims);
        }
        if (JwtTokenProvider.TYPE_GUEST.equals(type)) {
            return resolveGuest(claims);
        }
        if (JwtTokenProvider.TYPE_PLATFORM_ADMIN.equals(type) && allowPlatformAdmin) {
            return resolvePlatformAdmin(claims);
        }
        logger.debug("不接受的 token 類型: {}", type);
        return null;
    }

    /**
     * TENANT：由 claims 建立 AppUserDetails，不查 DB。
     */
    private UsernamePasswordAuthenticationToken resolveTenant(Claims claims) {
        Long brandId = JwtTokenProvider.getLongClaim(claims, JwtTokenProvider.CLAIM_BRAND_ID);
        Long userId = JwtTokenProvider.getLongClaim(claims, JwtTokenProvider.CLAIM_USER_ID);
        if (brandId == null || userId == null) {
            // 舊版 token（沒有 userId claim）或格式不完整：要求重新登入
            logger.debug("TENANT token 缺少 brandId 或 userId claim");
            return null;
        }

        // 停權／改權限後，簽發時間早於撤銷時間點的 token 一律拒絕
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        if (tokenRevocationService.isRevoked(userId, issuedAt)) {
            logger.debug("使用者 {} 的 token 已被撤銷", userId);
            return null;
        }

        Long storeId = JwtTokenProvider.getLongClaim(claims, JwtTokenProvider.CLAIM_STORE_ID);
        String role = claims.get(JwtTokenProvider.CLAIM_ROLE, String.class);
        AppUserDetails userDetails = AppUserDetails.fromClaims(userId, brandId, storeId, claims.getSubject(), role);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    /**
     * GUEST：建立 GuestPrincipal（ROLE_GUEST）。原本 REST Filter 直接拒絕訪客 token，導致線上點餐無法使用。
     */
    private UsernamePasswordAuthenticationToken resolveGuest(Claims claims) {
        String guestId = claims.getSubject();
        Long brandId = JwtTokenProvider.getLongClaim(claims, JwtTokenProvider.CLAIM_BRAND_ID);
        if (guestId == null || !guestId.startsWith(JwtTokenProvider.GUEST_SUBJECT_PREFIX) || brandId == null) {
            // 舊版訪客 token 沒有 brandId，無法做多租戶隔離，要求重新取得
            logger.debug("GUEST token 缺少 brandId 或 subject 格式錯誤");
            return null;
        }
        String displayName = claims.get(JwtTokenProvider.CLAIM_NAME, String.class);
        GuestPrincipal guest = new GuestPrincipal(guestId, brandId, displayName);
        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority(GuestPrincipal.ROLE_GUEST));
        return new UsernamePasswordAuthenticationToken(guest, null, authorities);
    }

    /**
     * PLATFORM_ADMIN：保留 DB 載入（無品牌歸屬，不設定 BrandContextHolder）。
     */
    private UsernamePasswordAuthenticationToken resolvePlatformAdmin(Claims claims) {
        try {
            UserDetails admin = platformAdminDetailsService.loadUserByUsername(claims.getSubject());
            return new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        } catch (UsernameNotFoundException e) {
            logger.debug("平台管理員不存在: {}", claims.getSubject());
            return null;
        }
    }
}
