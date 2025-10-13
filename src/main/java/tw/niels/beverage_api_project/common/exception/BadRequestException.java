package tw.niels.beverage_api_project.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 用於表示客戶端請求無效時的例外情況 (例如：參數錯誤)。
 * 當此例外被拋出且未被捕捉時，Spring 會自動回傳 HTTP 400 (Bad Request) 狀態碼。
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
