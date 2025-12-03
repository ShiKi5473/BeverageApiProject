package tw.niels.beverage_api_project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;
import java.util.Map;

@ControllerAdvice // 標註為全局異常處理器
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // 輔助方法：解析訊息
    private String resolveMessage(String code, Object[] args) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, locale);
        } catch (Exception e) {
            // 如果解析失敗 (例如 code 不存在)，直接回傳 code 本身，避免報錯
            return code;
        }
    }

    // 專門處理 BadRequestException
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequestException(BadRequestException ex, WebRequest request) {
        String message = resolveMessage(ex.getMessage(), ex.getArgs());
        logger.warn("BadRequest: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    // 專門處理 ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        String message = resolveMessage(ex.getMessage(), ex.getArgs());
        logger.warn("NotFound: {}", message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", message));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException ex, WebRequest request) {
        logger.warn("並發更新衝突: {}", ex.getMessage());
        return new ResponseEntity<>("系統忙碌中，請稍後重試。", HttpStatus.CONFLICT);
    }
    

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        logger.warn("操作衝突: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    // (可選) 處理其他未預期的異常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("未預期錯誤: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An internal server error occurred: " + ex.getMessage());
    }
}