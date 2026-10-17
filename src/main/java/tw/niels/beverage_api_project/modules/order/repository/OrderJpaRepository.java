package tw.niels.beverage_api_project.modules.order.repository;

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

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Spring Data JPA Repository
 * ============================================================
 *
 * 修改說明：
 * - 移除不限時間、不限筆數的 findAllByBrand_IdAndStore_Id / findAllByBrand_IdAndStore_IdAndStatus
 *   （會載入整店歷史訂單）；列表改用 findIdsByStore...OrderTimeRange + findAllWithDetailsByBrandIdAndIdIn。
 * - 移除報表統計查詢（findOrderStatusStats... / findPaymentStats... / findProductStats... /
 *   countOrdersByStoreIdAndOrderTimeBetweenAndStatusIn）：已由 report 模組的 OrderReportQueryRepository 取代。
 * - findByIdForSystem 的簽章與 entity graph 維持不變（庫存、報表、KDS 監聽者依賴）。
 * ============================================================
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

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
     * 新增：依線上下單請求 ID 查詢訂單本體（不載入品項），供 GET /online-orders/{ticketId}
     * 在 Redis ticket 尚未更新（仍為 QUEUED）時，以資料庫結果作為備援。
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.brand.id = :brandId AND o.requestId = :requestId")
    Optional<OrderEntity> findSummaryByBrandIdAndRequestId(@Param("brandId") Long brandId,
                                                           @Param("requestId") String requestId);

    /**
     * 新增：只查詢訂單目前狀態（線上訂單 ticket 查詢用，避免載入整張訂單）。
     */
    @Query("SELECT o.status FROM OrderEntity o WHERE o.brand.id = :brandId AND o.id = :orderId")
    Optional<OrderStatus> findStatusByBrandIdAndId(@Param("brandId") Long brandId,
                                                   @Param("orderId") Long orderId);

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
     * 新增（m-2 訂單流水號遺失）：查詢某分店某營業日「流水號最大」的訂單號碼，
     * 供 OrderNumberService 在 Redis 計數器不存在（Redis 重啟未持久化、FLUSHALL）時，以資料庫的最大流水號重新播種。
     *
     * 訂單號碼格式為 {storeId}-{yyyyMMdd}-{seq:04d}，prefixPattern 傳入 "{storeId}-{yyyyMMdd}-%"。
     * 先依長度、再依字串遞減排序：同一前綴下，流水號超過 4 位數（10000 以上）時字串較長，
     * 只用字串排序會讓 "9999" 排在 "10000" 前面。以 Pageable 限制筆數（只需要最前面幾筆）。
     * 系統內部用途：storeId 為全域唯一的 TSID，不需要 brandId 隔離（呼叫端只有訂單號碼產生器）。
     */
    @Query("SELECT o.orderNumber FROM OrderEntity o " +
            "WHERE o.store.id = :storeId AND o.orderNumber LIKE :prefixPattern " +
            "ORDER BY LENGTH(o.orderNumber) DESC, o.orderNumber DESC")
    List<String> findTopOrderNumbersByStoreIdAndPrefix(@Param("storeId") Long storeId,
                                                       @Param("prefixPattern") String prefixPattern,
                                                       Pageable pageable);

    /**
     * 系統內部用途：依訂單 ID 查詢（不含 brandId 隔離，僅供 Event Handler 等可信系統呼叫）
     * 包含品牌、店家、品項及規格關聯，供庫存扣減與報表統計使用。
     */
    @EntityGraph(attributePaths = {"brand", "store", "items", "items.productVariant", "items.options"})
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :orderId")
    Optional<OrderEntity> findByIdForSystem(@Param("orderId") Long orderId);

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
