package tw.niels.beverage_api_project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 全域例外處理：統一轉換為 {@link ErrorResponse}。
 * <p>
 * 【修正】原本只有 catch-all 的 {@code @ExceptionHandler(Exception.class)}，
 * 會把權限不足、JSON 格式錯誤、路徑不存在、型別不符等用戶端錯誤全部變成 500。
 * 新增明確的處理器，讓這些情況回傳正確的 4xx 狀態碼，catch-all 只處理真正的未預期錯誤。
 * </p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String resolveMessage(String code, Object[] args) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, locale);
        } catch (Exception e) {
            return code;
        }
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatusCode status, String error, String message) {
        return buildResponse(status, error, message, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatusCode status, String error, String message, Map<String, String> details) {
        ErrorResponse response = ErrorResponse.builder()
                .error(error)
                .message(message)
                .details(details)
                .build();
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        String message = resolveMessage(ex.getCode(), ex.getArgs());
        logger.warn("Domain Error: {}", message);
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_ERROR", message);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex) {
        String message = resolveMessage(ex.getCode(), ex.getArgs());
        logger.error("Application Error: {}", message);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "APPLICATION_ERROR", message);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex) {
        String message = resolveMessage(ex.getMessage(), ex.getArgs());
        logger.warn("Bad Request: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        String message = resolveMessage(ex.getMessage(), ex.getArgs());
        logger.warn("Not Found: {}", message);
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Optimistic Locking Failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "CONCURRENCY_ERROR", "系統忙碌中，資料已被其他人更新，請重新整理後再試。");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        logger.error("Data Integrity Violation: ", ex);
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_ERROR", "資料操作衝突，可能是重複鍵值或關聯約束。");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            // 【修正】物件層級（非欄位）的驗證錯誤不是 FieldError，原本強制轉型會 ClassCastException → 500
            String fieldName = (error instanceof FieldError fieldError) ? fieldError.getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        logger.warn("Validation failed: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "欄位驗證失敗", errors);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        logger.warn("Illegal State: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "STATE_ERROR", ex.getMessage());
    }

    // ===================== 【新增】安全性相關 =====================

    /**
     * 權限不足（含 @PreAuthorize 拒絕的 AuthorizationDeniedException）→ 403。
     * 原本被 catch-all 攔截變成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Access Denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "權限不足，無法執行此操作。");
    }

    /**
     * 認證失敗（例如平台登入帳密錯誤）→ 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        logger.warn("Authentication Failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "認證失敗：帳號或密碼不正確。");
    }

    // ===================== 【新增】請求格式錯誤 → 400 =====================

    /** JSON 格式錯誤、型別無法轉換（例如 enum 值不存在）→ 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "請求內容格式錯誤，無法解析。");
    }

    /** 路徑變數或查詢參數型別不符（例如 /orders/abc）→ 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        logger.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "參數 '" + ex.getName() + "' 的格式不正確。");
    }

    /** 缺少必要的查詢參數 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        logger.warn("Missing request parameter: {}", ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "缺少必要參數 '" + ex.getParameterName() + "'。");
    }

    /** 缺少必要的 Header → 400 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        logger.warn("Missing request header: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "缺少必要標頭 '" + ex.getHeaderName() + "'。");
    }

    /** 不合法的參數（例如 enum valueOf 失敗、TSID 解析失敗）→ 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Illegal Argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                ex.getMessage() != null ? ex.getMessage() : "請求參數不正確。");
    }

    // ===================== 【新增】HTTP 協定層錯誤 =====================

    /** HTTP 方法不支援 → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Method not supported: {}", ex.getMethod());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "不支援的 HTTP 方法: " + ex.getMethod());
    }

    /** Content-Type 不支援 → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        logger.warn("Media type not supported: {}", ex.getContentType());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "不支援的內容類型。");
    }

    /** 路徑不存在 → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        logger.debug("No resource found: {}", ex.getResourcePath());
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到請求的路徑。");
    }

    /**
     * ResponseStatusException：沿用例外本身的狀態碼（例如 RateLimitService 的 429、冪等處理中的 409）。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved != null ? resolved.name() : "HTTP_" + status.value();
        String message = ex.getReason() != null ? ex.getReason() : error;
        if (status.is5xxServerError()) {
            logger.error("ResponseStatusException: {} {}", status.value(), message);
        } else {
            logger.warn("ResponseStatusException: {} {}", status.value(), message);
        }
        return buildResponse(status, error, message);
    }

    // ===================== 【新增】SSE / 非同步請求 =====================

    /**
     * SSE 長連線逾時：回應早已開始串流（text/event-stream），無法再寫入 JSON，
     * 回傳 null 代表「已處理」，避免被 catch-all 當成 500 並印堆疊。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        logger.debug("Async request timeout (SSE)");
        return null;
    }

    /**
     * 用戶端已中斷連線（例如 KDS 頁面關閉）時寫入回應失敗，屬正常情況。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<ErrorResponse> handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        logger.debug("Async request not usable (client disconnected): {}", ex.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        logger.error("Unexpected Error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "發生未預期錯誤，請連繫管理員。");
    }
}
