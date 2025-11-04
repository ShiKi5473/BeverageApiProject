package tw.niels.beverage_api_project.modules.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 查詢特定品牌和店家的所有訂單 (依狀態篩選)
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.brandId = :brandId AND o.store.storeId = :storeId AND o.status = :status")
    List<Order> findAllByBrand_BrandIdAndStore_StoreIdAndStatus(Long brandId, Long storeId, OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.brandId = :brandId AND o.store.storeId = :storeId")
    List<Order> findAllByBrand_BrandIdAndStore_StoreId(Long brandId, Long storeId);

    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.items item " +
            "LEFT JOIN FETCH item.product " +
            "LEFT JOIN FETCH item.options " +
            "WHERE o.brand.brandId = :brandId AND o.orderId = :orderId")
    Optional<Order> findByBrand_BrandIdAndOrderId(Long brandId, Long orderId);
}
