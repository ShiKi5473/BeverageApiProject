package tw.niels.beverage_api_project.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tw.niels.beverage_api_project.security.jwt.JwtAuthChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker // 啟用 WebSocket 訊息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtAuthChannelInterceptor jwtAuthChannelInterceptor;

    // 【修改】允許來源改為與 REST CORS 相同的 app.cors.allowed-origins，不再允許任意來源 "*"
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtAuthChannelInterceptor jwtAuthChannelInterceptor,
                           @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthChannelInterceptor = jwtAuthChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. 設定訊息代理 (Broker)
        //    /queue: 點對點 (通知特定使用者，經由 /user 前綴轉換)
        //    /topic: 保留（目前沒有任何廣播；訂閱權限由 JwtAuthChannelInterceptor 控制，只允許 /user/queue/orders）
        registry.enableSimpleBroker("/topic", "/queue");

        // 2. 設定應用程式前綴 (前端送給後端的路徑前綴)
        registry.setApplicationDestinationPrefixes("/app");

        // 3. 設定使用者點對點推送的前綴 (預設是 /user)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 註冊 STOMP 端點
        registry.addEndpoint("/ws-kds") // 維持與前端 ws-client.js 一致的端點名稱
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS(); // 啟用 SockJS 支援
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 註冊 JWT 攔截器：CONNECT 驗證身分、SUBSCRIBE 檢查目的地
        registration.interceptors(jwtAuthChannelInterceptor);
    }
}
