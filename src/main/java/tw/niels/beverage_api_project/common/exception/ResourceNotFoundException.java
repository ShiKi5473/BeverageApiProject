package tw.niels.beverage_api_project.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String messageCode) {
        super(messageCode);
    }

    public ResourceNotFoundException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}