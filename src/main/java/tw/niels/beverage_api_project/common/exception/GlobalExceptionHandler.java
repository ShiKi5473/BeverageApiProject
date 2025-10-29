package tw.niels.beverage_api_project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice // 標註為全局異常處理器
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 專門處理 BadRequestException
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequestException(BadRequestException ex, WebRequest request) {
        // 記錄異常訊息和當前的 Authentication 狀態
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("Handling BadRequestException: '{}'. Current Authentication: {}", ex.getMessage(), authentication,
                ex);

        // 依賴 @ResponseStatus(HttpStatus.BAD_REQUEST) 返回 400
        // 您也可以在這裡手動構建 ResponseEntity
        // return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        // 如果 @ResponseStatus 不起作用，可以取消註解上面這行來強制返回 400
        // 但理論上 Spring 會處理 @ResponseStatus
        throw ex; // 重新拋出，讓 Spring 根據 @ResponseStatus 處理，或者被更高層級的處理器捕捉
    }

    // 專門處理 ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("Handling ResourceNotFoundException: '{}'. Current Authentication: {}", ex.getMessage(),
                authentication, ex);
        throw ex; // 重新拋出，讓 Spring 根據 @ResponseStatus(HttpStatus.NOT_FOUND) 處理
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