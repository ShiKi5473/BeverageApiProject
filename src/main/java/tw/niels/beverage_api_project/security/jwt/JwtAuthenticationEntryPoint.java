package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.common.exception.ErrorResponse;

/**
 * 未認證（401）處理。
 * <p>
 * 【修改】
 * 1. 改為 WARN 且不印堆疊：未帶 token / token 過期屬於常見的使用者端情況，原本每個 401 都用 ERROR 印完整堆疊造成 log 洪水。
 * 2. 回應改為統一的 JSON {@link ErrorResponse} 格式（原本 sendError 會轉到 /error 產生不同格式）。
 * </p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        // 只記錄訊息與 URI，不附帶例外物件（避免輸出堆疊）
        logger.warn("Unauthorized: {} (URI: {})", authException.getMessage(), request.getRequestURI());

        if (response.isCommitted()) {
            return;
        }
        ErrorResponse body = ErrorResponse.builder()
                .error("UNAUTHORIZED")
                .message("未登入或登入已失效，請重新登入。")
                .build();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
