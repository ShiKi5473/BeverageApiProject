package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.CustomUserDetailsService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // 注入 JWT 相關工具類別，用於生成、驗證和解析 Token
    private final JwtTokenProvider jwtTokenProvider;
    // 注入自定義的 UserDetailsService，用於從資料庫載入用戶資訊
    private final CustomUserDetailsService userDetailsService;

    // 依賴注入的建構函式，Spring 會自動注入 JwtTokenProvider 和 CustomUserDetailsService 實例
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
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

        // 從請求的 Header 中提取 JWT Token
        String token = getTokenFromRequest(request);

        // 檢查 Token 是否存在且有效
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                // 從 Token 中解析出 username 和 brandId
                String username = jwtTokenProvider.getUsernameFromJWT(token);
                Long brandId = jwtTokenProvider.getBrandIdFromJWT(token);

                BrandContextHolder.setBrandId(brandId);

                // 根據用戶名從資料庫中載入用戶的詳細資訊（UserDetails）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 建立一個 UsernamePasswordAuthenticationToken 物件，用於代表已驗證的用戶
                // 這個 Token 包含了用戶資訊（userDetails）、憑證（null，因為 JWT 不需密碼）和權限
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, // JWT Token 已經驗證過，不需要密碼，故設為 null
                        userDetails.getAuthorities()); // 取得用戶的權限列表

                // 設定認證 Token 的詳細資訊，包括客戶端的 IP 地址和 Session ID
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                filterLogger.debug("Setting Authentication in SecurityContextHolder: {}", authenticationToken);

                // 將認證 Token 設定到 Spring Security 的上下文中
                // 如此一來，後續的控制器和服務層就能夠知道是哪個用戶發出的請求，並進行權限檢查
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (ExpiredJwtException | MalformedJwtException | SignatureException | UnsupportedJwtException
                    | UsernameNotFoundException e) {
                // 這些是「預期中」的認證失敗：Token 本身有問題，或 Token 對應的用戶不存在
                // 使用 WARN 級別，因為這不是系統錯誤，而是客戶端的問題
                filterLogger.warn("Authentication failed: {}", e.getMessage());

                // 同樣要清除上下文
                BrandContextHolder.clear();
                SecurityContextHolder.clearContext();

            } catch (Exception e) {
                // 這些是「未預期」的系統錯誤 (例如 NullPointerException 或資料庫連線問題)
                // 這裡才真正需要使用 ERROR 級別
                filterLogger.error("Unexpected error setting user authentication", e);

                // 同樣要清除上下文
                BrandContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        } else {
            filterLogger.debug("JWT Token not found or invalid.");
        }
        try {
            // 呼叫下一個 filter 前打印狀態
            Authentication authBeforeDoFilter = SecurityContextHolder.getContext().getAuthentication();
            filterLogger.debug("Authentication before calling filterChain.doFilter(): {}", authBeforeDoFilter);

            filterChain.doFilter(request, response); // 繼續執行 Filter Chain (可能拋出例外)

            // 下一個 filter 執行完畢後打印狀態
            Authentication authAfterDoFilter = SecurityContextHolder.getContext().getAuthentication();
            filterLogger.debug("Authentication after calling filterChain.doFilter(): {}", authAfterDoFilter);

        } finally {
            BrandContextHolder.clear();
            filterLogger.debug("Cleaned BrandContextHolder.");
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