package tw.niels.beverage_api_project.common.exception;

/**
 * 領域層例外 (Domain Exception)
 *
 * 用於表示違反業務規則的情況。
 * 例如：訂單狀態不允許取消、餘額不足等。
 *
 * 注意：此類別屬於 common/exception，被所有 BC 的 Domain Layer 繼承，
 * 因此刻意不引入任何框架依賴（Lombok / Spring），以符合 Domain Layer 零框架原則。
 */
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

    /** 錯誤代碼（通常與 message 相同，供 i18n 查詢使用） */
    public String getCode() { return code; }

    /** 訊息插值參數（可為 null） */
    public Object[] getArgs() { return args; }
}
