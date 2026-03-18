package tw.niels.beverage_api_project.modules.order.domain.repository;

import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;

import java.util.List;
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
 * 與其他 Context 的互動：
 * - 此介面由 Application Layer 的 Handler 使用，用於載入和儲存 Order Aggregate
 * - Infrastructure Layer 的 OrderRepositoryImpl 實作此介面
 * ============================================================
 */
public interface OrderRepository {

    /**
     * 根據訂單 ID 查詢訂單（含品牌安全驗證）。
     *
     * @param brandId 品牌 ID（用於多租戶資料隔離，防止跨品牌存取）
     * @param orderId 訂單 ID（TSID）
     * @return 包含訂單的 Optional，若不存在或不屬於該品牌則回傳 empty
     */
    Optional<Order> findByBrandIdAndId(Long brandId, Long orderId);

    /**
     * 儲存訂單（新增或更新）。
     *
     * @param order 要儲存的訂單 Aggregate（含全部品項與快照）
     */
    void save(Order order);

    /**
     * 查詢指定品牌與店家的所有訂單（依狀態篩選）。
     * 用於 KDS 廚房顯示和 POS 訂單列表。
     *
     * @param brandId 品牌 ID
     * @param storeId 店家 ID
     * @param status  要篩選的訂單狀態
     * @return 符合條件的訂單清單（按建立時間排序）
     */
    List<Order> findByBrandIdAndStoreIdAndStatus(Long brandId, Long storeId, OrderStatus status);

    /**
     * 查詢指定品牌與店家的所有訂單（不限狀態）。
     * 用於 POS 的訂單歷史記錄。
     *
     * @param brandId 品牌 ID
     * @param storeId 店家 ID
     * @return 符合條件的訂單清單
     */
    List<Order> findByBrandIdAndStoreId(Long brandId, Long storeId);
}
