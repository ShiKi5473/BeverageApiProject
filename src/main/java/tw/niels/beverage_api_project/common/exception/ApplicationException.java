package tw.niels.beverage_api_project.common.exception;

/**
 * 應用層例外 (Application Exception)
 * 
 * 用於表示應用層協調過程中的錯誤，非純業務邏輯錯誤。
 * 例如：外部服務呼叫失敗、異步處理異常等。
 *
 * 注意：與 DomainException 一致，刻意不引入 Lombok，
 * 統一 common/exception 層的零框架依賴風格。
 */
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

    /** 錯誤代碼（通常與 message 相同，供 i18n 查詢使用） */
    public String getCode() { return code; }

    /** 訊息插值參數（可為 null） */
    public Object[] getArgs() { return args; }
}
