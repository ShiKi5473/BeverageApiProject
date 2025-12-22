package tw.niels.beverage_api_project.security.jwt;

import com.mongodb.lang.NonNullApi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import io.jsonwebtoken.MalformedJwtException;

@Component
public class JwtAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthChannelInterceptor.class);
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService tenantUserDetailsService;

    public JwtAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                     @Qualifier("customUserDetailsService") UserDetailsService tenantUserDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tenantUserDetailsService = tenantUserDetailsService;
    }

    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 1. 只在 "CONNECT" (連線) 階段進行驗證
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            // 2. 從 STOMP 標頭讀取 'Authorization' (這需要前端配合傳入)
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            logger.debug("WebSocket Auth Header: {}", authHeader);

            String token = null;
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            if (token != null && jwtTokenProvider.validateToken(token)) {
                try {
                    String username = jwtTokenProvider.getUsernameFromJWT(token);
                    String userType = jwtTokenProvider.getTypeFromJWT(token);

                    // 3. KDS 必須是 TENANT (員工)
                    if ("TENANT".equals(userType)) {
                        Long brandId = jwtTokenProvider.getBrandIdFromJWT(token);
                        if (brandId == null) {
                            throw new MalformedJwtException("TENANT token is missing brandId claim.");
                        }
                        BrandContextHolder.setBrandId(brandId);

                        UserDetails userDetails = tenantUserDetailsService.loadUserByUsername(username);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                        // 4. 【關鍵】將認證資訊存入 SecurityContext，並附加到 WebSocket Session
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        accessor.setUser(authentication); // 附加到 STOMP Session

                        BrandContextHolder.clear();
                    }
                } catch (Exception e) {
                    logger.warn("WebSocket 認證失敗: {}", e.getMessage());
                    BrandContextHolder.clear();
                }
            }
        }
        return message;
    }
}