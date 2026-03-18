package tw.niels.beverage_api_project.modules.order.domain.model;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Value Object（Enum）
 * ============================================================
 *
 * 訂單狀態枚舉（Domain Model 版本）
 *
 * 定義訂單完整的生命週期狀態，並內建允許的狀態轉換矩陣，
 * 供 Order Aggregate Root 的業務方法使用。
 *
 * 狀態機流程：
 * <pre>
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │                       訂單狀態機                              │
 *   ├─────────────────┬────────────────────────────────────────────┤
 *   │ PENDING         │ 現場點餐：已建立，尚未付款                    │
 *   │ HELD            │ 現場點餐：已暫存（可繼續修改或結帳）            │
 *   │ AWAITING_ACCEPT │ 線上點餐：已付款，等待店家接單                 │
 *   │ PREPARING       │ 製作中（POS 結帳後 或 店家接單後）             │
 *   │ READY_FOR_PICKUP│ 製作完成，等待顧客取餐                        │
 *   │ CLOSED          │ 訂單完成（顧客取餐）                          │
 *   │ CANCELLED       │ 訂單取消                                     │
 *   └─────────────────┴────────────────────────────────────────────┘
 *
 *   合法轉換：
 *   PENDING             → HELD（hold()）
 *   PENDING / HELD      → PREPARING（processPayment()）
 *   PENDING / HELD      → CANCELLED（cancel()）
 *   AWAITING_ACCEPTANCE → PREPARING（accept()）
 *   AWAITING_ACCEPTANCE → CANCELLED（cancel()）
 *   PREPARING           → READY_FOR_PICKUP（markReadyForPickup()）
 *   PREPARING           → CANCELLED（cancel()）
 *   READY_FOR_PICKUP    → CLOSED（close()）
 *   READY_FOR_PICKUP    → CANCELLED（cancel()）
 * </pre>
 *
 * 注意：此 Enum 為 Domain Layer 版本，與 `modules/order/enums/OrderStatus.java`
 * 的 JPA 層版本共存，待 Phase 3（Infrastructure 分離）完成後再合併。
 * ============================================================
 */
public enum OrderStatus {

    /** 現場點餐：訂單已建立，尚未結帳付款 */
    PENDING,

    /** 現場點餐：訂單已暫存，可繼續修改品項或進行結帳 */
    HELD,

    /** 線上點餐：顧客已付款，等待店家確認接單 */
    AWAITING_ACCEPTANCE,

    /** 製作中：POS 結帳後或店家接單後進入此狀態，廚房正在製作 */
    PREPARING,

    /** 待取餐：製作完成，等待顧客取餐（KDS 觸發） */
    READY_FOR_PICKUP,

    /** 訂單完成：顧客已取餐，訂單建檔歸檔 */
    CLOSED,

    /** 訂單取消：任何允許的狀態皆可被取消 */
    CANCELLED
}
