package tw.niels.beverage_api_project.modules.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Date;

import org.springframework.data.domain.Pageable;
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
 * Bounded Context: Order
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

    // 修改：entity graph 加入 paymentMethod，讓查詢端組 OrderResponseDto 時不必再延遲載入付款方式
    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options", "paymentMethod"})
    @Query("SELECT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.id = :orderId")
    Optional<OrderEntity> findByBrand_IdAndId(Long brandId, Long orderId);

    /**
     * 新增：依線上下單請求 ID（ticketId）查詢訂單，供 MQ 消費者做冪等檢查。
     */
    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options"})
    @Query("SELECT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.requestId = :requestId")
    Optional<OrderEntity> findByBrand_IdAndRequestId(@Param("brandId") Long brandId,
                                                     @Param("requestId") String requestId);

    /**
     * 新增：訂單列表第一步 — 依店家與下單時間區間取出訂單 ID（依時間新到舊、以 Pageable 限制筆數）。
     * 分兩步查詢（先 ID 再抓明細）是為了避免「集合 fetch + 分頁」造成 Hibernate 在記憶體中分頁。
     */
    @Query("SELECT o.id FROM OrderEntity o " +
            "WHERE o.brand.id = :brandId AND o.store.id = :storeId " +
            "AND o.orderTime >= :from AND o.orderTime < :to " +
            "ORDER BY o.orderTime DESC")
    List<Long> findIdsByStoreAndOrderTimeRange(@Param("brandId") Long brandId,
                                               @Param("storeId") Long storeId,
                                               @Param("from") Date from,
                                               @Param("to") Date to,
                                               Pageable pageable);

    /**
     * 新增：訂單列表第一步（含狀態篩選），使用 V18 的 (store_id, status_code, order_time) 索引。
     */
    @Query("SELECT o.id FROM OrderEntity o " +
            "WHERE o.brand.id = :brandId AND o.store.id = :storeId AND o.status = :status " +
            "AND o.orderTime >= :from AND o.orderTime < :to " +
            "ORDER BY o.orderTime DESC")
    List<Long> findIdsByStoreAndStatusAndOrderTimeRange(@Param("brandId") Long brandId,
                                                        @Param("storeId") Long storeId,
                                                        @Param("status") OrderStatus status,
                                                        @Param("from") Date from,
                                                        @Param("to") Date to,
                                                        Pageable pageable);

    /**
     * 新增：訂單列表第二步 — 依 ID 一次載入訂單與品項、規格、選項、付款方式（單一 SQL，避免 N+1）。
     */
    @EntityGraph(attributePaths = {"items", "items.product", "items.productVariant", "items.options", "paymentMethod"})
    @Query("SELECT DISTINCT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.id IN :ids")
    List<OrderEntity> findAllWithDetailsByBrandIdAndIdIn(@Param("brandId") Long brandId,
                                                         @Param("ids") Collection<Long> ids);

    /**
     * 系統內部用途：依訂單 ID 查詢（不含 brandId 隔離，僅供 Event Handler 等可信系統呼叫）
     * 包含品牌、店家、品項及規格關聯，供庫存扣減與報表統計使用。
     */
    @EntityGraph(attributePaths = {"brand", "store", "items", "items.productVariant", "items.options"})
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :orderId")
    Optional<OrderEntity> findByIdForSystem(@Param("orderId") Long orderId);

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
