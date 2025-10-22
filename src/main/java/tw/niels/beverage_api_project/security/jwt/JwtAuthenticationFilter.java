package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.CustomUserDetailsService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
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

                // 將認證 Token 設定到 Spring Security 的上下文中
                // 如此一來，後續的控制器和服務層就能夠知道是哪個用戶發出的請求，並進行權限檢查
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
            // 繼續執行過濾器鏈中的下一個過濾器
            finally {
                BrandContextHolder.clear();
            }
        }
        filterChain.doFilter(request, response);

    }

    /**
     * 從 HTTP 請求的 "Authorization" Header 中提取 Bearer Token。
     * Authorization: Bearer <Token>
     * 
     * @param request HTTP 請求
     * @return 提取出的 JWT Token 字串，如果沒有則返回 null
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // 檢查 Header 是否存在且以 "Bearer " 開頭
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // 截取 "Bearer " 之後的部分，即為實際的 JWT Token
            return bearerToken.substring(7);
        }

        return null;
    }
}