package tw.niels.beverage_api_project.config;

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

    public WebSocketConfig(JwtAuthChannelInterceptor jwtAuthChannelInterceptor) {
        this.jwtAuthChannelInterceptor = jwtAuthChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. 設定訊息代理 (Broker)
        //    /topic: 廣播 (如 KDS)
        //    /queue: 點對點 (如 通知特定使用者)
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
                .setAllowedOriginPatterns("*") // 開發環境允許跨域
                .withSockJS(); // 啟用 SockJS 支援
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 註冊 JWT 攔截器，在連線建立階段驗證身分
        registration.interceptors(jwtAuthChannelInterceptor);
    }
}