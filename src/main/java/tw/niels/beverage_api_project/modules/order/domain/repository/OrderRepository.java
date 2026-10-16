package tw.niels.beverage_api_project.modules.order.domain.repository;

import tw.niels.beverage_api_project.modules.order.domain.model.Order;

import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 訂單倉儲介面（Domain Repository Interface）
 *
 * 由 Domain Layer 定義，Infrastructure Layer 實作。
 * 此設計符合「依賴反轉原則（DIP）」：
 * Domain 不依賴 JPA/Spring，而是由 Infrastructure 實作此純 Java 介面。
 *
 * 注意：此介面不繼承 JpaRepository，保持 Domain Layer 零框架依賴。
 * JPA 相關操作封裝在 Infrastructure Layer 的 OrderRepositoryImpl 中。
 *
 * 修改說明：
 * - 新增 nextId()：訂單 ID 改為建立前預先產生，讓領域事件攜帶非 null 的 orderId。
 * - 新增 findByRequestId()：線上訂單消費者以 requestId（ticketId）做冪等檢查。
 * - 移除列表查詢方法：列表屬於 CQRS 讀取端，改由 OrderQueryHandler 直接查詢 OrderEntity。
 * ============================================================
 */
public interface OrderRepository {

    /**
     * 產生新的訂單 ID（TSID）。
     * 由 Application Layer 在呼叫 Order.place() / placeOnline() 前取得。
     *
     * @return 新的訂單 ID
     */
    Long nextId();

    /**
     * 根據訂單 ID 查詢訂單（含品牌安全驗證）。
     *
     * @param brandId 品牌 ID（用於多租戶資料隔離，防止跨品牌存取）
     * @param orderId 訂單 ID（TSID）
     * @return 包含訂單的 Optional，若不存在或不屬於該品牌則回傳 empty
     */
    Optional<Order> findByBrandIdAndId(Long brandId, Long orderId);

    /**
     * 依線上下單請求 ID 查詢訂單（冪等檢查用）。
     *
     * @param brandId   品牌 ID
     * @param requestId 線上下單請求 ID（ticketId）
     * @return 已建立的訂單，若尚未建立則為 empty
     */
    Optional<Order> findByRequestId(Long brandId, String requestId);

    /**
     * 儲存訂單（新增或更新）。
     * 新訂單（{@link Order#isNew()}）會新增；既有訂單會更新同交易中受管的實體。
     *
     * @param order 要儲存的訂單 Aggregate（含全部品項與快照）
     */
    void save(Order order);
}
