package tw.niels.beverage_api_project.modules.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 查詢特定品牌和店家的所有訂單 (依狀態篩選)
    List<Order> findAllByBrand_BrandIdAndStore_StoreIdAndStatus(Long brandId, Long storeId, OrderStatus status);

    // 查詢特定品牌和店家的所有訂單
    List<Order> findAllByBrand_BrandIdAndStore_StoreId(Long brandId, Long storeId);

    // 查詢特定品牌的單一訂單 (確保多租戶隔離)
    Optional<Order> findByBrand_BrandIdAndOrderId(Long brandId, Long orderId);
}
