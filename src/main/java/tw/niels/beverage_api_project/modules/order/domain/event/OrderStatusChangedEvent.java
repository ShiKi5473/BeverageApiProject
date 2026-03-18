package tw.niels.beverage_api_project.modules.order.domain.event;

import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Domain Event
 * ============================================================
 *
 * 訂單狀態變更事件（Domain Event）
 *
 * 觸發時機：訂單狀態發生任何合法轉換時發布，包含：
 * - PENDING → HELD（暫存）
 * - PENDING / HELD → PREPARING（結帳）
 * - AWAITING_ACCEPTANCE → PREPARING（店家接單）
 * - PREPARING → READY_FOR_PICKUP（製作完成）
 *
 * 訂閱者：
 * - KDS Application：根據新狀態更新廚房顯示
 * - Notification Application：推播狀態通知給顧客
 *
 * 注意：訂單完成（CLOSED）及取消（CANCELLED）因有額外語意，
 * 分別使用專屬的 {@link OrderClosedEvent} 和 {@link OrderCancelledEvent}。
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final。
 * ============================================================
 */
public record OrderStatusChangedEvent(
        /** 狀態變更的訂單 ID（TSID） */
        Long orderId,

        /** 變更前的訂單狀態 */
        OrderStatus oldStatus,

        /** 變更後的訂單狀態 */
        OrderStatus newStatus,

        /** 事件發生的時間戳記（UTC） */
        Instant occurredAt
) {
    /**
     * 靜態工廠方法，自動填入事件發生時間。
     *
     * @param orderId   訂單 ID
     * @param oldStatus 變更前狀態
     * @param newStatus 變更後狀態
     * @return 新的 OrderStatusChangedEvent
     */
    public static OrderStatusChangedEvent of(Long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        return new OrderStatusChangedEvent(orderId, oldStatus, newStatus, Instant.now());
    }
}
