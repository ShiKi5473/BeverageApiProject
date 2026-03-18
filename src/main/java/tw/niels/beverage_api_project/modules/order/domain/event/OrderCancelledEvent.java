package tw.niels.beverage_api_project.modules.order.domain.event;

import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Domain Event
 * ============================================================
 *
 * 訂單取消事件（Domain Event）
 *
 * 觸發時機：呼叫 {@code Order.cancel()} 成功後發布，
 * 代表訂單被取消，可能需要觸發退款、退還積點、退還庫存等後續動作。
 *
 * cancelledFromStatus 的用途：
 * 下游系統需要知道取消前的狀態才能決定補償動作：
 * - PENDING / HELD：未付款，無需退款
 * - AWAITING_ACCEPTANCE / PREPARING / READY_FOR_PICKUP：已付款，需觸發退款流程
 *
 * 訂閱者：
 * - Member Application：退還已使用的積點（pointsUsed > 0 時）
 * - Payment Application：觸發退款流程（若 cancelledFromStatus 為已付款狀態）
 * - Inventory Application：若庫存已扣減，退還庫存（視業務規則決定）
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final。
 * ============================================================
 */
public record OrderCancelledEvent(
        /** 被取消的訂單 ID（TSID） */
        Long orderId,

        /**
         * 取消前的訂單狀態（即「從哪個狀態被取消」）
         * 供下游系統判斷是否需要退款或其他補償動作
         */
        OrderStatus cancelledFromStatus,

        /**
         * 此訂單原本使用的積點數量
         * Member Application 以此退還積點（若 > 0 且會員存在）
         */
        Long pointsUsed,

        /**
         * 會員 ID（若為訪客訂單則為 null）
         * Member Application 以此定位會員進行退點
         */
        Long memberId,

        /** 事件發生的時間戳記（UTC） */
        Instant occurredAt
) {
    /**
     * 靜態工廠方法，自動填入事件發生時間。
     *
     * @param orderId             訂單 ID
     * @param cancelledFromStatus 取消前的狀態
     * @param pointsUsed          已使用積點（需退還）
     * @param memberId            會員 ID（可為 null）
     * @return 新的 OrderCancelledEvent
     */
    public static OrderCancelledEvent of(Long orderId, OrderStatus cancelledFromStatus,
                                         Long pointsUsed, Long memberId) {
        return new OrderCancelledEvent(orderId, cancelledFromStatus, pointsUsed, memberId, Instant.now());
    }
}
