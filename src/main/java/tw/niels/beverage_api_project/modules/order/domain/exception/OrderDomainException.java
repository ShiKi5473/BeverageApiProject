package tw.niels.beverage_api_project.modules.order.domain.exception;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Exception
 * ============================================================
 *
 * 訂單領域例外（Domain Exception）
 *
 * 用於表示業務規則違反，例如：
 * - 嘗試在不允許的狀態下執行操作（如 CLOSED 訂單無法取消）
 * - 業務不變量（Invariant）被破壞
 *
 * 此例外不依賴任何框架，由 Application Layer 捕捉後轉換為
 * 對應的 HTTP 回應（如 400 Bad Request）。
 * ============================================================
 */
public class OrderDomainException extends RuntimeException {

    /**
     * 建立一個訂單領域例外。
     *
     * @param message 描述業務規則違反的訊息，例如「訂單狀態為 CLOSED，無法執行取消操作」
     */
    public OrderDomainException(String message) {
        super(message);
    }
}
