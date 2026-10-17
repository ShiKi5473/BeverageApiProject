package tw.niels.beverage_api_project.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tw.niels.beverage_api_project.security.TenantPrincipal;

/**
 * STOMP 入站訊息攔截器。
 * <p>
 * 【安全性修正】
 * <ul>
 *   <li>CONNECT 沒有有效的 TENANT / GUEST token 時直接拋出例外拒絕（原本無效 token 也會被接受）</li>
 *   <li>不再寫入 SecurityContextHolder：clientInboundChannel 使用執行緒池，寫入後未清除會洩漏到其他連線</li>
 *   <li>session user 改為 {@link StompPrincipal}，getName() = principalKey（不再使用手機號碼）</li>
 *   <li>SUBSCRIBE 只允許 /user/queue/orders；SEND 等其他會帶目的地的指令一律拒絕（目前沒有 @MessageMapping）</li>
 * </ul>
 * </p>
 */
@Component
public class JwtAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthChannelInterceptor.class);

    /** 唯一允許訂閱的目的地（點對點訂單通知） */
    private static final String ALLOWED_SUBSCRIPTION = "/user/queue/orders";

    private final JwtPrincipalResolver jwtPrincipalResolver;

    public JwtAuthChannelInterceptor(JwtPrincipalResolver jwtPrincipalResolver) {
        this.jwtPrincipalResolver = jwtPrincipalResolver;
    }

    // 【修改】改用 Spring 的 @NonNull（原本的 org.jetbrains @NotNull 只是間接依賴，非專案宣告的相依套件）
    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            // 非 STOMP frame（例如 heartbeat）直接放行
            return message;
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> {
                // 目前沒有任何 @MessageMapping，前端不需要送訊息給後端
                throw new MessageDeliveryException("不允許傳送訊息至: " + accessor.getDestination());
            }
            default -> {
                // DISCONNECT / UNSUBSCRIBE / ACK 等不需額外檢查
            }
        }
        return message;
    }

    /**
     * CONNECT：驗證 Authorization header 的 JWT（只接受 TENANT / GUEST），失敗即拒絕連線。
     */
    private void authenticate(StompHeaderAccessor accessor) {
        // 從 STOMP 標頭讀取 'Authorization'（不記錄內容，避免 token 進入 log）
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 不接受平台管理員 token（allowPlatformAdmin = false）
        UsernamePasswordAuthenticationToken authentication = jwtPrincipalResolver.resolve(token, false);
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal tenant)) {
            logger.warn("WebSocket CONNECT 被拒絕：缺少或無效的 token");
            // 拋出例外 → StompSubProtocolHandler 回傳 ERROR frame 並關閉連線
            throw new MessageDeliveryException("未授權的 WebSocket 連線");
        }

        // 只設定在 STOMP session 上，不寫入 SecurityContextHolder（避免執行緒池洩漏）
        accessor.setUser(new StompPrincipal(tenant.principalKey(), tenant));
    }

    /**
     * SUBSCRIBE：只允許已認證的 session 訂閱 /user/queue/orders。
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        // 只接受 CONNECT 時由本攔截器設定的 StompPrincipal（principalKey），其他來源的 Principal 一律拒絕
        if (!(accessor.getUser() instanceof StompPrincipal)) {
            throw new MessageDeliveryException("未認證的 session 不可訂閱");
        }
        String destination = accessor.getDestination();
        if (!ALLOWED_SUBSCRIPTION.equals(destination)) {
            logger.warn("拒絕訂閱目的地: {}", destination);
            throw new MessageDeliveryException("不允許訂閱: " + destination);
        }
    }
}
