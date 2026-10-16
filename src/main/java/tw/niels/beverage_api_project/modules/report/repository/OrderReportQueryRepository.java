package tw.niels.beverage_api_project.modules.report.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Report
 * Layer: Infrastructure - Spring Data JPA Repository (報表專用的訂單唯讀查詢)
 * ============================================================
 *
 * 日結報表專用的訂單統計查詢。
 *
 * <p>為什麼不放在 OrderJpaRepository：Order 模組的 repository 由訂單核心維護，
 * 報表自己的查詢放在報表模組，避免跨模組同時修改同一個檔案。</p>
 *
 * <p>[B-6 / B-7] 與舊查詢的差異：</p>
 * <ul>
 *   <li>{@link OrderEntity#getOrderTime()} / {@link OrderEntity#getCompletedTime()} 是 {@code java.util.Date}，
 *       這裡的時間參數一律綁定 {@code Date}（由營業時區計算），舊查詢綁 LocalDateTime 型別不一致。</li>
 *   <li>已完成 (CLOSED) 訂單以 completedTime 為準；已取消 (CANCELLED) 訂單從不寫入 completedTime，
 *       改以 orderTime 判斷是哪一天的取消單，否則取消數永遠是 0。</li>
 *   <li>付款統計只算有付款方式的 CLOSED 訂單（沒有付款方式的不列入細分）。</li>
 * </ul>
 */
public interface OrderReportQueryRepository extends Repository<OrderEntity, Long> {

    /** 日結時視為「尚未結案」的訂單狀態（日結當下仍有這些單據時要記錄警告） */
    List<OrderStatus> UNCLOSED_STATUSES = List.of(
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.PENDING,
            OrderStatus.HELD,
            OrderStatus.AWAITING_ACCEPTANCE);

    /**
     * 一次查出單店單日各狀態的統計（只需 1 次 SQL，取代原本 3 個查詢）：
     * <ul>
     *   <li>CLOSED：依 completedTime 落在區間內</li>
     *   <li>CANCELLED：依 orderTime 落在區間內（取消單沒有 completedTime）</li>
     *   <li>未結案狀態：依 orderTime 落在區間內（用來計算「未結案數」警告，整店只需一次）</li>
     * </ul>
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto(" +
            "o.status, COUNT(o), SUM(o.totalAmount), SUM(o.discountAmount), SUM(o.finalAmount)) " +
            "FROM OrderEntity o " +
            "WHERE o.store.id = :storeId AND (" +
            "  (o.status = :closed AND o.completedTime BETWEEN :start AND :end) " +
            "  OR (o.status = :cancelled AND o.orderTime BETWEEN :start AND :end) " +
            "  OR (o.status IN :unclosedStatuses AND o.orderTime BETWEEN :start AND :end)" +
            ") " +
            "GROUP BY o.status")
    List<OrderStatusStatsDto> findStatusStats(@Param("storeId") Long storeId,
                                              @Param("closed") OrderStatus closed,
                                              @Param("cancelled") OrderStatus cancelled,
                                              @Param("unclosedStatuses") Collection<OrderStatus> unclosedStatuses,
                                              @Param("start") Date start,
                                              @Param("end") Date end);

    /** 便利方法：使用固定的 CLOSED / CANCELLED / 未結案狀態集合 */
    default List<OrderStatusStatsDto> findDailyStatusStats(Long storeId, Date start, Date end) {
        return findStatusStats(storeId, OrderStatus.CLOSED, OrderStatus.CANCELLED, UNCLOSED_STATUSES, start, end);
    }

    /**
     * 付款方式統計：只統計「有付款方式」的 CLOSED 訂單（INNER JOIN，沒有付款方式的訂單自然被忽略）。
     * 結果依付款方式代碼分組（CASH / CREDIT_CARD / THIRD_PARTY）。
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto(" +
            "pm.code, SUM(o.finalAmount)) " +
            "FROM OrderEntity o " +
            "JOIN o.paymentMethod pm " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = :status " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY pm.code")
    List<PaymentStatsDto> findPaymentStats(@Param("storeId") Long storeId,
                                           @Param("status") OrderStatus status,
                                           @Param("start") Date start,
                                           @Param("end") Date end);

    default List<PaymentStatsDto> findClosedPaymentStats(Long storeId, Date start, Date end) {
        return findPaymentStats(storeId, OrderStatus.CLOSED, start, end);
    }

    /** 商品銷售統計：CLOSED 訂單，依 completedTime 落在區間內 */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto(" +
            "i.product.id, i.product.name, SUM(i.quantity), SUM(i.subtotal)) " +
            "FROM OrderItemEntity i " +
            "JOIN i.order o " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = :status " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY i.product.id, i.product.name")
    List<ProductSalesStatsDto> findProductStats(@Param("storeId") Long storeId,
                                                @Param("status") OrderStatus status,
                                                @Param("start") Date start,
                                                @Param("end") Date end);

    default List<ProductSalesStatsDto> findClosedProductStats(Long storeId, Date start, Date end) {
        return findProductStats(storeId, OrderStatus.CLOSED, start, end);
    }
}
