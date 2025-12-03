package tw.niels.beverage_api_project.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends AppException {

    public BadRequestException(String messageCode) {
        super(messageCode);
    }

    public BadRequestException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}