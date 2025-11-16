// 檔案： .../config/WebSocketConfig.java (新檔案)
package tw.niels.beverage_api_project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tw.niels.beverage_api_project.security.jwt.JwtAuthChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker // 【關鍵】啟用 WebSocket 訊息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtAuthChannelInterceptor jwtAuthChannelInterceptor;

    public WebSocketConfig(JwtAuthChannelInterceptor  jwtAuthChannelInterceptor) {
        this.jwtAuthChannelInterceptor = jwtAuthChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. 設定訊息代理 (Broker)
        //    /topic 是 KDS 訂閱的主題前綴 (P2P 廣播)
        registry.enableSimpleBroker("/topic");

        // 2. (可選) 設定應用程式前綴
        //    如果 KDS 需要主動傳送訊息給後端 (例如 "完成訂單")，會用這個
            registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-kds")
                .setAllowedOriginPatterns(
                        "http://localhost:63342",
                        "http://127.0.0.1:63342",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                )                // 3. (可選) 啟用 SockJS 作為備援方案
                .withSockJS();
    }

    @Override // 【新增】
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 1. 將我們的 JWT 攔截器註冊到訊息通道 (Channel)
        registration.interceptors(jwtAuthChannelInterceptor);
    }

}