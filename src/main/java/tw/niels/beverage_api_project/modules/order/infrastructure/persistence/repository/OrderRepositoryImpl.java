package tw.niels.beverage_api_project.modules.order.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.util.TsidUtil;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.infrastructure.mapper.OrderMapper;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * 修改說明（P0-1：聚合更新必失敗）：
 * - 原本 save() 每次都以 OrderMapper.toEntity() 建立全新的 OrderEntity 並呼叫 jpaRepository.save()；
 *   因 BaseTsidEntity.isNew() 為 true，Spring Data 走 persist()，而同交易中已有同 ID 的受管實體
 *   → NonUniqueObjectException（任何既有訂單的更新都會失敗）。
 * - 現在：
 *   - 新訂單（order.isNew()）：建立新的 OrderEntity 並 persist；
 *   - 既有訂單：取得同交易中受管的 OrderEntity（Handler 先前已載入，通常不會再查 DB），
 *     把 Domain 狀態套用上去，交由 dirty checking 寫回，@Version 樂觀鎖同時生效。
 * - 移除反射呼叫 setId() 與 System.err（ID 改為建立前由 nextId() 指派）。
 * ============================================================
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** 新增：預先產生訂單 ID（TSID），讓 Domain 事件在建立當下就帶有 orderId */
    @Override
    public Long nextId() {
        return TsidUtil.nextId();
    }

    @Override
    public Optional<Order> findByBrandIdAndId(Long brandId, Long orderId) {
        return jpaRepository.findByBrand_IdAndId(brandId, orderId)
            .map(OrderMapper::toDomain);
    }

    /** 新增：依線上下單請求 ID 查詢（冪等檢查） */
    @Override
    public Optional<Order> findByRequestId(Long brandId, String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return jpaRepository.findByBrand_IdAndRequestId(brandId, requestId)
            .map(OrderMapper::toDomain);
    }

    @Override
    public void save(Order order) {
        if (order.isNew()) {
            // ─── 新訂單：建立新實體並 persist ───────────────────────────────
            OrderEntity entity = OrderMapper.toNewEntity(order, entityManager);
            entityManager.persist(entity);
            // 標記為已持久化，避免同交易內再次 save 時重複 persist
            order.markPersisted();
            return;
        }

        // ─── 既有訂單：更新同交易中受管的實體 ──────────────────────────────
        // em.find 會優先回傳持久化內容中已載入的實體（Handler 剛以 findByBrandIdAndId 載入），不會再查 DB
        OrderEntity managed = entityManager.find(OrderEntity.class, order.getId());
        if (managed == null || managed.getBrand() == null
                || !Objects.equals(managed.getBrand().getId(), order.getBrandId())) {
            throw new ResourceNotFoundException("找不到訂單: " + order.getId());
        }
        OrderMapper.applyTo(managed, order, entityManager);
    }
}
