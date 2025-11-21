package tw.niels.beverage_api_project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@ControllerAdvice // 標註為全局異常處理器
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 專門處理 BadRequestException
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequestException(BadRequestException ex, WebRequest request) {
        // 記錄異常訊息和當前的 Authentication 狀態
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("處理請求時發生錯誤: {}。路徑: {}", ex.getMessage(), request.getDescription(false));

        // 依賴 @ResponseStatus(HttpStatus.BAD_REQUEST) 返回 400
        // 您也可以在這裡手動構建 ResponseEntity
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));        // 如果 @ResponseStatus 不起作用，可以取消註解上面這行來強制返回 400
        // 但理論上 Spring 會處理 @ResponseStatus
        // throw ex; // 重新拋出，讓 Spring 根據 @ResponseStatus 處理，或者被更高層級的處理器捕捉
    }

    // 專門處理 ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("Handling ResourceNotFoundException: '{}'. Current Authentication: {}", ex.getMessage(),
                authentication, ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
        // throw ex; // 重新拋出，讓 Spring 根據 @ResponseStatus(HttpStatus.NOT_FOUND) 處理
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException ex, WebRequest request) {

        logger.warn("並發更新衝突: {}", ex.getMessage());

        // 回傳 HTTP 409 Conflict
        return new ResponseEntity<>("系統忙碌中，點數更新失敗，請稍後重試。", HttpStatus.CONFLICT);
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        logger.warn("操作請求無效/衝突: {}", ex.getMessage());
        // 回傳 JSON 格式錯誤
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    // (可選) 處理其他未預期的異常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.error("Handling unexpected Exception: '{}'. Current Authentication: {}", ex.getMessage(), authentication,
                ex);

        // 對於未預期的錯誤，返回 500 Internal Server Error
        return new ResponseEntity<>("An internal server error occurred: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}