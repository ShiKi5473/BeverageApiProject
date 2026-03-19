package tw.niels.beverage_api_project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return buildResponse(status, error, message, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message, Map<String, String> details) {
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
            String fieldName = ((FieldError) error).getField();
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        logger.error("Unexpected Error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "發生未預期錯誤，請連繫管理員。");
    }
}