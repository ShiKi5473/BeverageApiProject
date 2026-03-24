package tw.niels.beverage_api_project.modules.order.domain.model;

import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 訂單 ID 值物件
 *
 * 封裝 Long 型態的 TSID，確保 ID 不為 null 並提升型別安全性。
 *
 * 不可變（Immutable）：使用 Java record。
 * ============================================================
 */
public record OrderId(Long value) {

    /**
     * 緊湊建構子：驗證 ID 不得為 null。
     *
     * @throws OrderDomainException 若 value 為 null
     */
    public OrderId {
        if (value == null) {
            throw new OrderDomainException("訂單 ID 不得為 null");
        }
    }

    /**
     * 靜態工廠方法。
     *
     * @param value 原始 ID 數值
     * @return 新的 OrderId 實例
     */
    public static OrderId of(Long value) {
        return new OrderId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
