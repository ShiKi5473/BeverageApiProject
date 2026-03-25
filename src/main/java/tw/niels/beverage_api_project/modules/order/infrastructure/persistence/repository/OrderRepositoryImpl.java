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
 *
 * Order BC Domain Repository 的 Infrastructure 層實現
 *
 * 此類負責：
 * 1. 實現 Domain 層的 OrderRepository 介面
 * 2. 委派 Spring Data JPA 的 OrderJpaRepository 執行資料庫操作
 * 3. 使用 OrderMapper 進行 Entity ↔ Domain Model 的雙向轉換
 * 4. 確保 Domain Layer 完全獨立於 JPA 和 Spring 框架
 *
 * 架構說明：
 * - Domain OrderRepository 定義業務合約（WHAT），本類實現它（HOW）
 * - 所有持久化邏輯透過 OrderJpaRepository（Spring Data JPA）執行
 * - 所有返回值由 Mapper 轉換為 Domain Model
 * - 多租戶隔離邏輯統一在此層完成
 *
 * ============================================================
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    // 委派的 Spring Data JPA Repository
    private final OrderJpaRepository jpaRepository;

    /**
     * 建構子：注入 JPA Repository
     *
     * @param jpaRepository Spring Data JPA 的 OrderJpaRepository
     */
    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * 根據訂單 ID 查詢訂單（含品牌安全驗證）
     *
     * 流程：
     * 1. 呼叫 JPA Repository 查詢（已包含多租戶檢查）
     * 2. 若找到，使用 Mapper 轉換為 Domain Model
     * 3. 返回 Optional 包裝的 Domain Order
     *
     * @param brandId 品牌 ID（多租戶隔離）
     * @param orderId 訂單 ID（TSID）
     * @return 包含訂單的 Optional，若不存在或不屬於該品牌則回傳 empty
     */
    @Override
    public Optional<Order> findByBrandIdAndId(Long brandId, Long orderId) {
        // JPA 層查詢（多租戶檢查已內建於 JPA 查詢）
        return jpaRepository.findByBrand_IdAndId(brandId, orderId)
            // 轉換為 Domain Model
            .map(OrderMapper::toDomain);
    }

    /**
     * 儲存訂單（新增或更新）
     *
     * 流程：
     * 1. 使用 Mapper 將 Domain Model 轉換為 JPA Entity
     * 2. 委派至 JPA Repository 執行持久化
     * 3. 若是新增（無 ID），同步資料庫生成的 ID 回 Domain Model
     *
     * @param order 要儲存的訂單 Domain Aggregate（含全部品項與快照）
     */
    @Override
    public void save(Order order) {
        // 轉換為 Entity（用 Mapper 進行轉換）
        var jpaEntity = OrderMapper.toEntity(order);

        // 委派至 JPA Repository 執行持久化（INSERT 或 UPDATE）
        var savedEntity = jpaRepository.save(jpaEntity);

        // 若是新訂單（無 ID），同步資料庫生成的 TSID 回 Domain Model
        if (order.getId() == null && savedEntity.getId() != null) {
            try {
                // 使用反射設定 Domain Model 的 ID（通常是 package-private 方法）
                var setIdMethod = Order.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(order, savedEntity.getId());
            } catch (Exception e) {
                // 反射失敗時記錄警告（但不中斷流程）
                System.err.println("警告：無法同步訂單 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    /**
     * 查詢指定品牌與店家的所有訂單（依狀態篩選）
     *
     * 用於 KDS（廚房顯示系統）和 POS 訂單列表，通常篩選特定狀態。
     *
     * 流程：
     * 1. 呼叫 JPA Repository 查詢（含 EntityGraph 最佳化）
     * 2. 轉換結果清單為 Domain Model
     *
     * @param brandId 品牌 ID
     * @param storeId 店家 ID
     * @param status  要篩選的訂單狀態
     * @return 符合條件的訂單清單（按建立時間排序），若無符合則返回空清單
     */
    @Override
    public List<Order> findByBrandIdAndStoreIdAndStatus(Long brandId, Long storeId, OrderStatus status) {
        // JPA 層查詢（已包含 EntityGraph 最佳化，一次載入品項與選項）
        return jpaRepository.findAllByBrand_IdAndStore_IdAndStatus(brandId, storeId, status)
            .stream()
            // 轉換每個 Entity 為 Domain Model
            .map(OrderMapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * 查詢指定品牌與店家的所有訂單（不限狀態）
     *
     * 用於 POS 的訂單歷史記錄或全量查詢。
     *
     * 流程：
     * 1. 呼叫 JPA Repository 查詢所有訂單（不篩選狀態）
     * 2. 轉換結果清單為 Domain Model
     *
     * @param brandId 品牌 ID
     * @param storeId 店家 ID
     * @return 該品牌與店家的所有訂單清單
     */
    @Override
    public List<Order> findByBrandIdAndStoreId(Long brandId, Long storeId) {
        // JPA 層查詢（已包含 EntityGraph 最佳化）
        return jpaRepository.findAllByBrand_IdAndStore_Id(brandId, storeId)
            .stream()
            // 轉換每個 Entity 為 Domain Model
            .map(OrderMapper::toDomain)
            .collect(Collectors.toList());
    }
}
