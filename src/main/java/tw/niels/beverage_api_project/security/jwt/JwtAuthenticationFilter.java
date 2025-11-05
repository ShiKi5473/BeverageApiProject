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
import tw.niels.beverage_api_project.security.BrandContextHolder;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // 注入 JWT 相關工具類別，用於生成、驗證和解析 Token
    private final JwtTokenProvider jwtTokenProvider;
    // 注入自定義的 UserDetailsService，用於從資料庫載入用戶資訊
    private final UserDetailsService tenantUserDetailsService;
    private final UserDetailsService platformAdminDetailsService; // 新增的

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   @Qualifier("customUserDetailsService") UserDetailsService tenantUserDetailsService, // 使用 @Qualifier
                                   @Qualifier("platformAdminDetailsService") UserDetailsService platformAdminDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tenantUserDetailsService = tenantUserDetailsService;
        this.platformAdminDetailsService = platformAdminDetailsService;
    }

    /**
     * 核心過濾器方法，每次請求都會執行此方法。
     * 它的職責是從請求中提取 JWT Token，並將用戶資訊設定到 Spring Security 的上下文中。
     */
    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        filterLogger.info("JwtAuthenticationFilter processing request: {}", request.getRequestURI());

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                String username = jwtTokenProvider.getUsernameFromJWT(token);
                String userType = jwtTokenProvider.getTypeFromJWT(token); // 取得使用者類型

                UserDetails userDetails;

                // 【關鍵修改】
                if ("PLATFORM_ADMIN".equals(userType)) {
                    // 如果是平台管理員，呼叫 PlatformAdminDetailsService
                    // 不設定 BrandContextHolder
                    userDetails = platformAdminDetailsService.loadUserByUsername(username);

                } else if ("TENANT".equals(userType)) {
                    // 如果是品牌員工，呼叫 CustomUserDetailsService
                    Long brandId = jwtTokenProvider.getBrandIdFromJWT(token);
                    if (brandId == null) {
                        throw new MalformedJwtException("TENANT token is missing brandId claim.");
                    }
                    BrandContextHolder.setBrandId(brandId); // 設定 Brand 上下文
                    userDetails = tenantUserDetailsService.loadUserByUsername(username);

                } else {
                    throw new MalformedJwtException("Invalid token type: " + userType);
                }

                // --- 後續授權邏輯 (保持不變) ---
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            } catch (Exception e) {
                filterLogger.warn("Authentication failed: {}", e.getMessage());
                BrandContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        } else {
            filterLogger.debug("JWT Token not found or invalid.");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            BrandContextHolder.clear(); // 確保 BrandContextHolder 總是被清除
            // SecurityContextHolder 在此不應被清除，除非有例外
        }
    }

    /**
     * 從 HTTP 請求的 "Authorization" Header 中提取 Bearer Token。
     * Authorization: Bearer <Token>
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