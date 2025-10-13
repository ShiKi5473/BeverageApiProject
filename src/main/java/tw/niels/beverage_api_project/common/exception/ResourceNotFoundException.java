package tw.niels.beverage_api_project.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 用於表示找不到特定資源時的例外情況。
 * 當此例外被拋出且未被捕捉時，Spring 會自動回傳 HTTP 404 (Not Found) 狀態碼。
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND)

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
