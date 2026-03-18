package tw.niels.beverage_api_project.modules.order.domain.model;

import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 訂單號碼值物件（Value Object）
 *
 * 封裝訂單號碼字串，確保格式正確且不為空。
 * 訂單號碼由 Application Layer 的 OrderNumberGenerator 負責生成，
 * 再以此 Value Object 包裝後傳入 Order Aggregate。
 *
 * 不可變（Immutable）：欄位為 final，無 setter。
 * ============================================================
 */
public record OrderNumber(String value) {

    /**
     * 緊湊建構子：驗證訂單號碼不得為空。
     *
     * @throws OrderDomainException 若 value 為 null 或空字串
     */
    public OrderNumber {
        if (value == null || value.isBlank()) {
            throw new OrderDomainException("訂單號碼不得為空");
        }
    }

    /**
     * 靜態工廠方法，從字串建立訂單號碼。
     *
     * @param value 訂單號碼字串
     * @return 新的 OrderNumber 實例
     */
    public static OrderNumber of(String value) {
        return new OrderNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
