package tw.niels.beverage_api_project.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tw.niels.beverage_api_project.common.exception.ErrorResponse;

/**
 * 【新增】權限不足（403）處理：Filter Chain 層級（URL 授權）被拒時回傳統一的 JSON {@link ErrorResponse}。
 * <p>
 * Controller 層的 @PreAuthorize 拒絕則由 GlobalExceptionHandler 的 AccessDeniedException 處理器回傳相同格式。
 * </p>
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // 權限不足屬於預期中的業務情況，WARN 且不印堆疊
        logger.warn("Access denied: {} (URI: {})", accessDeniedException.getMessage(), request.getRequestURI());

        if (response.isCommitted()) {
            return;
        }
        ErrorResponse body = ErrorResponse.builder()
                .error("FORBIDDEN")
                .message("權限不足，無法執行此操作。")
                .build();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
