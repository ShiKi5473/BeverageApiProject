package tw.niels.beverage_api_project.modules.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 查詢特定品牌和店家的所有訂單 (依狀態篩選)
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.id = :brandId AND o.store.id = :storeId AND o.status = :status")
    List<Order> findAllByBrand_IdAndStore_IdAndStatus(Long brandId, Long storeId, OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.id = :brandId AND o.store.id = :storeId")
    List<Order> findAllByBrand_IdAndStore_Id(Long brandId, Long storeId);

    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.id = :brandId AND o.id = :orderId")
    Optional<Order> findByBrand_IdAndId(Long brandId, Long orderId);

    /**
     * 統計 1: 訂單狀態統計 (改用 DTO)
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto(" +
            "o.status, COUNT(o), SUM(o.totalAmount), SUM(o.discountAmount), SUM(o.finalAmount)) " +
            "FROM Order o " +
            "WHERE o.store.id = :storeId " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY o.status")
    List<OrderStatusStatsDto> findOrderStatusStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                                      @Param("start") LocalDateTime start,
                                                                      @Param("end") LocalDateTime end);

    /**
     * 統計 2: 付款方式統計 (改用 DTO)
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto(" +
            "pm.code, SUM(o.finalAmount)) " +
            "FROM Order o " +
            "LEFT JOIN o.paymentMethod pm " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = 'CLOSED' " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY pm.code")
    List<PaymentStatsDto> findPaymentStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                              @Param("start") LocalDateTime start,
                                                              @Param("end") LocalDateTime end);

    /**
     * 統計 3: 商品銷售統計 (改用 DTO)
     */
    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto(" +
            "i.product.id, i.product.name, SUM(i.quantity), SUM(i.subtotal)) " +
            "FROM OrderItem i " +
            "JOIN i.order o " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = 'CLOSED' " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY i.product.id, i.product.name")
    List<ProductSalesStatsDto> findProductStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                                   @Param("start") LocalDateTime start,
                                                                   @Param("end") LocalDateTime end);
    /**
     * 計算指定分店在指定時間內，處於特定狀態的訂單數量
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = :storeId AND o.orderTime BETWEEN :start AND :end AND o.status IN :statuses")
    long countOrdersByStoreIdAndOrderTimeBetweenAndStatusIn(
            @Param("storeId") Long storeId,
            @Param("start") Date start,
            @Param("end") Date end,
            @Param("statuses") Collection<OrderStatus> statuses
    );

    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndOrderId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<Order> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndOrderId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<Order> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用包含 brandId 的自訂查詢");
    }

    /**
     * 禁用預設的 deleteById。
     */
    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 deleteById()，請先驗證 BrandId 再進行刪除");
    }

    /**
     * 禁用預設的 existsById。
     */
    @Deprecated
    @Override
    default boolean existsById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 existsById()，請改用包含 brandId 的自訂查詢");
    }


}
