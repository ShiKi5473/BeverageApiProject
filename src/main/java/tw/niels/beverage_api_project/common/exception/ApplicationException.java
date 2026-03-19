package tw.niels.beverage_api_project.common.exception;

import lombok.Getter;

/**
 * 應用層例外 (Application Exception)
 * 
 * 用於表示應用層協調過程中的錯誤，非純業務邏輯錯誤。
 * 例如：外部服務呼叫失敗、異步處理異常等。
 */
@Getter
public class ApplicationException extends RuntimeException {
    private final String code;
    private final Object[] args;

    public ApplicationException(String message) {
        super(message);
        this.code = message;
        this.args = null;
    }

    public ApplicationException(String message, Object... args) {
        super(message);
        this.code = message;
        this.args = args;
    }
}
