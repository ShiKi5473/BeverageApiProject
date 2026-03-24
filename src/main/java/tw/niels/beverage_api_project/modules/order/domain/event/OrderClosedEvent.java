package tw.niels.beverage_api_project.modules.order.domain.event;

import tw.niels.beverage_api_project.common.domain.vo.Money;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Domain Event
 * ============================================================
 *
 * 訂單完成事件（Domain Event）
 *
 * 觸發時機：呼叫 {@code Order.close()} 成功後發布，
 * 代表顧客已完成取餐，訂單進入 CLOSED 最終態。
 *
 * 與 {@link OrderStatusChangedEvent} 的區別：
 * 此事件攜帶財務資訊（最終金額、積點），供下游系統執行後續動作，
 * 而不只是通知狀態改變。
 *
 * 訂閱者：
 * - Inventory Application：扣減庫存（依訂單品項對應的食材配方）
 * - Member Application：累計會員積點（pointsEarned）
 * - Report Application：更新每日銷售統計
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final。
 * ============================================================
 */
public record OrderClosedEvent(
        /** 完成的訂單 ID（TSID） */
        Long orderId,

        /** 訂單的最終實付金額（已扣除折扣與點數折抵） */
        Money finalAmount,

        /** 本次訂單累積的積點數量（0 表示無會員或不符合積點條件） */
        Long pointsEarned,

        /**
         * 會員 ID（若為訪客訂單則為 null）
         * Member Application 以此判斷是否需要累積積點
         */
        Long memberId,

        /** 事件發生的時間戳記（UTC） */
        Instant occurredAt
) {
    /**
     * 靜態工廠方法，自動填入事件發生時間。
     *
     * @param orderId      訂單 ID
     * @param finalAmount  最終實付金額
     * @param pointsEarned 累積積點數
     * @param memberId     會員 ID（可為 null）
     * @return 新的 OrderClosedEvent
     */
    public static OrderClosedEvent of(Long orderId, Money finalAmount, Long pointsEarned, Long memberId) {
        return new OrderClosedEvent(orderId, finalAmount, pointsEarned, memberId, Instant.now());
    }
}
