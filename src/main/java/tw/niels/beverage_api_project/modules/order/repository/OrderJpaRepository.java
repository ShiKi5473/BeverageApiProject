package tw.niels.beverage_api_project.modules.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Date;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;

/**
 * ============================================================
 * Bounded Context: OrderEntity
 * Layer: Infrastructure - Spring Data JPA Repository
 * ============================================================
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    
    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options"})
    @Query("SELECT DISTINCT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.store.id = :storeId AND o.status = :status")
    List<OrderEntity> findAllByBrand_IdAndStore_IdAndStatus(Long brandId, Long storeId, OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options"})
    @Query("SELECT DISTINCT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.store.id = :storeId")
    List<OrderEntity> findAllByBrand_IdAndStore_Id(Long brandId, Long storeId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options"})
    @Query("SELECT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.id = :orderId")
    Optional<OrderEntity> findByBrand_IdAndId(Long brandId, Long orderId);

    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto(" +
            "o.status, COUNT(o), SUM(o.totalAmount), SUM(o.discountAmount), SUM(o.finalAmount)) " +
            "FROM OrderEntity o " +
            "WHERE o.store.id = :storeId " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY o.status")
    List<OrderStatusStatsDto> findOrderStatusStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                                      @Param("start") LocalDateTime start,
                                                                      @Param("end") LocalDateTime end);

    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto(" +
            "pm.code, SUM(o.finalAmount)) " +
            "FROM OrderEntity o " +
            "LEFT JOIN o.paymentMethod pm " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = 'CLOSED' " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY pm.code")
    List<PaymentStatsDto> findPaymentStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                              @Param("start") LocalDateTime start,
                                                              @Param("end") LocalDateTime end);

    @Query("SELECT new tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto(" +
            "i.product.id, i.product.name, SUM(i.quantity), SUM(i.subtotal)) " +
            "FROM OrderItemEntity i " +
            "JOIN i.order o " +
            "WHERE o.store.id = :storeId " +
            "AND o.status = 'CLOSED' " +
            "AND o.completedTime BETWEEN :start AND :end " +
            "GROUP BY i.product.id, i.product.name")
    List<ProductSalesStatsDto> findProductStatsByStoreAndDateRange(@Param("storeId") Long storeId,
                                                                   @Param("start") LocalDateTime start,
                                                                   @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.store.id = :storeId AND o.orderTime BETWEEN :start AND :end AND o.status IN :statuses")
    long countOrdersByStoreIdAndOrderTimeBetweenAndStatusIn(
            @Param("storeId") Long storeId,
            @Param("start") Date start,
            @Param("end") Date end,
            @Param("statuses") Collection<OrderStatus> statuses
    );

    @Deprecated
    @Override
    @NonNull
    default Optional<OrderEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndOrderId() 以確保資料隔離");
    }

    @Deprecated
    @Override
    @NonNull
    default List<OrderEntity> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用包含 brandId 的自訂查詢");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 deleteById()，請先驗證 BrandId 再進行刪除");
    }

    @Deprecated
    @Override
    default boolean existsById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 existsById()，請改用包含 brandId 的自訂查詢");
    }
}
