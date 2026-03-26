package tw.niels.beverage_api_project.modules.order.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.infrastructure.mapper.OrderMapper;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Order> findByBrandIdAndId(Long brandId, Long orderId) {
        return jpaRepository.findByBrand_IdAndId(brandId, orderId)
            .map(OrderMapper::toDomain);
    }

    @Override
    public void save(Order order) {
        var jpaEntity = OrderMapper.toEntity(order);
        var savedEntity = jpaRepository.save(jpaEntity);

        if (order.getId() == null && savedEntity.getId() != null) {
            try {
                var setIdMethod = Order.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(order, savedEntity.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步訂單 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    @Override
    public List<Order> findByBrandIdAndStoreIdAndStatus(Long brandId, Long storeId, OrderStatus status) {
        tw.niels.beverage_api_project.modules.order.enums.OrderStatus jpaStatus =
            tw.niels.beverage_api_project.modules.order.enums.OrderStatus.valueOf(status.name());
        return jpaRepository.findAllByBrand_IdAndStore_IdAndStatus(brandId, storeId, jpaStatus)
            .stream()
            .map(OrderMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByBrandIdAndStoreId(Long brandId, Long storeId) {
        return jpaRepository.findAllByBrand_IdAndStore_Id(brandId, storeId)
            .stream()
            .map(OrderMapper::toDomain)
            .collect(Collectors.toList());
    }
}
