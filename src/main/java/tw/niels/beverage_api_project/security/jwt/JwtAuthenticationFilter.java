package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.modules.auth.service.SseTicketService;
import tw.niels.beverage_api_project.modules.auth.service.SseTicketService.SseTicketPayload;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.TenantPrincipal;

/**
 * JWT 認證過濾器。
 * <p>
 * 【重構重點】
 * <ul>
 *   <li>token 只解析一次，TENANT 直接由 claims 建立身分（不再每個請求查 DB），撤銷由 TokenRevocationService 處理</li>
 *   <li>接受 GUEST token（建立 GuestPrincipal / ROLE_GUEST），修正線上點餐訪客被拒的問題</li>
 *   <li>SSE ticket 改由 ticket 內的身分 JSON 重建 principal，修正 SSE 永遠 401 的問題</li>
 *   <li>每請求 log 改為 DEBUG，避免 INFO log 洪水</li>
 *   <li>此 @Component 由 SecurityConfig 的 FilterRegistrationBean 停用自動註冊，只在 Security Filter Chain 中執行一次</li>
 * </ul>
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** SSE 端點路徑（只有此端點接受 ticket 參數，避免其他 API 的 query 參數名稱衝突） */
    private static final String SSE_STREAM_PATH_SUFFIX = "/kds/stream";

    private final JwtPrincipalResolver jwtPrincipalResolver;
    private final SseTicketService sseTicketService;

    public JwtAuthenticationFilter(JwtPrincipalResolver jwtPrincipalResolver,
                                   SseTicketService sseTicketService) {
        this.jwtPrincipalResolver = jwtPrincipalResolver;
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

        // 【修改】每請求 log 改為 DEBUG
        filterLogger.debug("JwtAuthenticationFilter processing request: {}", request.getRequestURI());

        try {
            UsernamePasswordAuthenticationToken authentication = null;

            // ===== 認證方式一：SSE 一次性 Ticket（僅限 SSE 端點） =====
            // ticket 由 POST /api/v1/auth/sse-ticket 產生，存於 Redis，30 秒 TTL，使用後立即銷毀
            String ticket = request.getParameter("ticket");
            if (StringUtils.hasText(ticket) && request.getRequestURI().endsWith(SSE_STREAM_PATH_SUFFIX)) {
                authentication = authenticateWithSseTicket(ticket);
            } else {
                // ===== 認證方式二：標準 JWT Bearer Token =====
                String token = getTokenFromRequest(request);
                if (StringUtils.hasText(token)) {
                    // 允許平台管理員 token（平台 API 需要）
                    authentication = jwtPrincipalResolver.resolve(token, true);
                    if (authentication == null) {
                        filterLogger.debug("JWT Token invalid, revoked or unsupported type.");
                    }
                }
            }

            if (authentication != null) {
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 員工／會員／訪客皆設定多租戶上下文；平台管理員無品牌歸屬，不設定
                if (authentication.getPrincipal() instanceof TenantPrincipal tenant) {
                    BrandContextHolder.setBrandId(tenant.brandId());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            BrandContextHolder.clear(); // 確保 BrandContextHolder 總是被清除（執行緒會被 Tomcat 重用）
        }
    }

    /**
     * 使用一次性 SSE Ticket 進行認證。
     * Ticket 經 Redis 驗證後立即刪除（single-use），由 ticket 內存放的身分資料直接重建 AppUserDetails。
     */
    private UsernamePasswordAuthenticationToken authenticateWithSseTicket(String ticket) {
        // validateAndConsumeTicket 為原子操作：取出身分並刪除 ticket（確保一次性使用）
        Optional<SseTicketPayload> payload = sseTicketService.validateAndConsumeTicket(ticket);
        if (payload.isEmpty()) {
            filterLogger.debug("SSE ticket 驗證失敗（無效或已過期）");
            return null;
        }
        AppUserDetails userDetails = payload.get().toUserDetails();
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
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
