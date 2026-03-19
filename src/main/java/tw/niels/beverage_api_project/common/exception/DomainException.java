package tw.niels.beverage_api_project.common.exception;

import lombok.Getter;

/**
 * 領域層例外 (Domain Exception)
 * 
 * 用於表示違反業務規則的情況。
 * 例如：訂單狀態不允許取消、餘額不足等。
 */
@Getter
public class DomainException extends RuntimeException {
    private final String code;
    private final Object[] args;

    public DomainException(String message) {
        super(message);
        this.code = message;
        this.args = null;
    }

    public DomainException(String message, Object... args) {
        super(message);
        this.code = message;
        this.args = args;
    }
}
