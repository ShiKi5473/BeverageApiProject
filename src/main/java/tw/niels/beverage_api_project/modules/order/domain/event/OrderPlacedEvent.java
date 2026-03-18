package tw.niels.beverage_api_project.modules.order.domain.event;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Domain Event
 * ============================================================
 *
 * 訂單建立事件（Domain Event）
 *
 * 觸發時機：呼叫 {@code Order.place()} 成功建立訂單後立即發布。
 *
 * 訂閱者：
 * - Report Application：記錄新訂單至當日統計
 * - KDS Application：通知廚房有新訂單（AWAITING_ACCEPTANCE 流程）
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final。
 * ============================================================
 */
public record OrderPlacedEvent(
        /** 新建立的訂單 ID（TSID） */
        Long orderId,

        /** 訂單所屬品牌 ID */
        Long brandId,

        /** 訂單所屬店家 ID */
        Long storeId,

        /** 訂單號碼（人類可讀，如 A001） */
        String orderNumber,

        /** 事件發生的時間戳記（UTC） */
        Instant occurredAt
) {
    /**
     * 建立訂單事件的靜態工廠方法，自動填入事件發生時間。
     *
     * @param orderId     訂單 ID
     * @param brandId     品牌 ID
     * @param storeId     店家 ID
     * @param orderNumber 訂單號碼
     * @return 新的 OrderPlacedEvent
     */
    public static OrderPlacedEvent of(Long orderId, Long brandId, Long storeId, String orderNumber) {
        return new OrderPlacedEvent(orderId, brandId, storeId, orderNumber, Instant.now());
    }
}
