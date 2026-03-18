package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.modules.auth.service.SseTicketService;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.BrandContextHolder;

@Component
public class  JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // 注入 JWT 相關工具類別，用於生成、驗證和解析 Token
    private final JwtTokenProvider jwtTokenProvider;
    // 注入自定義的 UserDetailsService，用於從資料庫載入用戶資訊
    private final UserDetailsService tenantUserDetailsService;
    private final UserDetailsService platformAdminDetailsService;
    private final SseTicketService sseTicketService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   @Qualifier("customUserDetailsService") UserDetailsService tenantUserDetailsService,
                                   @Qualifier("platformAdminDetailsService") UserDetailsService platformAdminDetailsService,
                                   SseTicketService sseTicketService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tenantUserDetailsService = tenantUserDetailsService;
        this.platformAdminDetailsService = platformAdminDetailsService;
        this.sseTicketService = sseTicketService;
    }

    /**
     * 核心過濾器方法，每次請求都會執行此方法。
     * 支援兩種認證方式：
     * 1. 標準 JWT Bearer Token（從 Authorization Header 讀取）
     * 2. 一次性 SSE Ticket（從 Query Parameter "ticket" 讀取）
     *    — 因為瀏覽器 EventSource API 不支援自訂 Header，
     *      所以 SSE 連線改用短期一次性 ticket 取代直接傳遞 JWT，
     *      避免 JWT 出現在 URL、server log、proxy log 或瀏覽器歷史中。
     */
    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        filterLogger.info("JwtAuthenticationFilter processing request: {}", request.getRequestURI());

        // ===== 認證方式一：SSE 一次性 Ticket =====
        // 優先檢查 ticket 參數（僅用於 SSE 連線場景）
        // ticket 由 POST /api/v1/auth/sse-ticket 產生，存於 Redis，30 秒 TTL，使用後立即銷毀
        String ticket = request.getParameter("ticket");
        if (StringUtils.hasText(ticket)) {
            authenticateWithSseTicket(ticket, request);
        } else {
            // ===== 認證方式二：標準 JWT Bearer Token =====
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                authenticateWithJwt(token, request);
            } else {
                filterLogger.debug("JWT Token not found or invalid.");
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            BrandContextHolder.clear(); // 確保 BrandContextHolder 總是被清除
        }
    }

    /**
     * 使用一次性 SSE Ticket 進行認證。
     * Ticket 經 Redis 驗證後立即刪除（single-use），從中取得 username 再載入 UserDetails。
     */
    private void authenticateWithSseTicket(String ticket, HttpServletRequest request) {
        try {
            // validateAndConsumeTicket 為原子操作：取出 username 並刪除 ticket（確保一次性使用）
            String username = sseTicketService.validateAndConsumeTicket(ticket);
            if (username == null) {
                filterLogger.warn("SSE ticket 驗證失敗（無效或已過期）");
                return;
            }

            // 透過 username 載入完整的使用者資訊
            UserDetails userDetails = tenantUserDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            // 從 UserDetails 中取得 brandId，設定多租戶上下文
            if (userDetails instanceof AppUserDetails appUser) {
                BrandContextHolder.setBrandId(appUser.brandId());
            }

        } catch (Exception e) {
            filterLogger.warn("SSE ticket 認證失敗: {}", e.getMessage());
            BrandContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 使用標準 JWT Bearer Token 進行認證。
     * 根據 Token 中的 type claim 區分平台管理員與品牌租戶，載入對應的 UserDetails。
     */
    private void authenticateWithJwt(String token, HttpServletRequest request) {
        try {
            String username = jwtTokenProvider.getUsernameFromJWT(token);
            String userType = jwtTokenProvider.getTypeFromJWT(token);

            UserDetails userDetails;

            if ("PLATFORM_ADMIN".equals(userType)) {
                // 平台管理員：不設定 BrandContextHolder（無品牌歸屬）
                userDetails = platformAdminDetailsService.loadUserByUsername(username);

            } else if ("TENANT".equals(userType)) {
                // 品牌租戶：從 Token 取出 brandId，設定多租戶上下文
                Long brandId = jwtTokenProvider.getBrandIdFromJWT(token);
                if (brandId == null) {
                    throw new MalformedJwtException("TENANT token is missing brandId claim.");
                }
                BrandContextHolder.setBrandId(brandId);
                userDetails = tenantUserDetailsService.loadUserByUsername(username);

            } else {
                throw new MalformedJwtException("Invalid token type: " + userType);
            }

            // 將認證資訊存入 SecurityContext，供後續 Controller / Service 使用
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        } catch (Exception e) {
            filterLogger.warn("Authentication failed: {}", e.getMessage());
            BrandContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 從 HTTP 請求的 "Authorization" Header 中提取 Bearer Token。
     * 僅從 Header 讀取，不再支援 Query Parameter（已改用 SSE Ticket 機制）。
     *
     * @param request HTTP 請求
     * @return 提取出的 JWT Token 字串，如果沒有則返回 null
     */
    private String getTokenFromRequest(@Nonnull HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // 檢查 Header 是否存在且以 "Bearer " 開頭
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // 截取 "Bearer " 之後的部分，即為實際的 JWT Token
            return bearerToken.substring(7);
        }
        return null;
    }
}