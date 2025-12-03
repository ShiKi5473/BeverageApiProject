package tw.niels.beverage_api_project.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservabilityConfig {

    /**
     * 過濾掉不需要追蹤的請求
     * 例如：Actuator 健康檢查、Swagger UI 資源
     */
    @Bean
    ObservationPredicate noActuatorServerObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String uri = serverContext.getCarrier().getRequestURI();
                return !uri.startsWith("/actuator") &&
                        !uri.startsWith("/swagger-ui") &&
                        !uri.startsWith("/v3/api-docs");
            }
            return true;
        };
    }
}