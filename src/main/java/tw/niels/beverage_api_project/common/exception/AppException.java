package tw.niels.beverage_api_project.common.exception;

/**
 * 應用程式基礎異常
 * 支援 I18n 訊息代碼與參數
 */
public abstract class AppException extends RuntimeException {

    private final Object[] args;

    public AppException(String messageCode) {
        super(messageCode);
        this.args = null;
    }

    public AppException(String messageCode, Object... args) {
        super(messageCode);
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }
}