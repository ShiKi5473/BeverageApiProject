package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem as OrderItemJpaEntity;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * OrderItem 映射工具
 *
 * 負責 OrderItemJpaEntity 與 OrderItem Domain Entity 的雙向轉換。
 * OrderItem 是 Order Aggregate 內部的 Entity，生命週期由 Order 管理。
 *
 * ============================================================
 */
public class OrderItemMapper {

    /**
     * JPA OrderItem Entity → Domain OrderItem（讀取資料庫）
     *
     * @param entity JPA OrderItem Entity
     * @return Domain OrderItem，若 entity 為 null 返回 null
     */
    public static OrderItem toDomain(OrderItemJpaEntity entity) {
        if (entity == null) return null;

        // 使用 Domain OrderItem 的靜態工廠方法建立
        OrderItem item = OrderItem.create(
            entity.getProduct() != null ? entity.getProduct().getId() : entity.getProductId(),
            entity.getProductVariant() != null ? entity.getProductVariant().getId() : entity.getVariantId(),
            entity.getQuantity(),
            Money.of(entity.getUnitPrice()),
            entity.getNotes(),
            // 轉換選項 ID 集合
            entity.getOptions() != null
                ? entity.getOptions().stream()
                    .map(opt -> opt.getId())
                    .collect(Collectors.toList())
                : Collections.emptyList(),
            entity.getProductSnapshot()
        );

        // 設定 OrderItem 的 ID（由 Infrastructure 設定）
        try {
            var setIdMethod = OrderItem.class.getDeclaredMethod("setId", Long.class);
            setIdMethod.setAccessible(true);
            setIdMethod.invoke(item, entity.getId());
        } catch (Exception e) {
            // 若反射失敗，日誌記錄但不中斷
            System.err.println("警告：無法設定 OrderItem ID");
        }

        return item;
    }

    /**
     * Domain OrderItem → JPA OrderItem Entity（寫入資料庫）
     *
     * @param domain Domain OrderItem
     * @return JPA OrderItem Entity，若 domain 為 null 返回 null
     */
    public static OrderItemJpaEntity toEntity(OrderItem domain) {
        if (domain == null) return null;

        OrderItemJpaEntity entity = new OrderItemJpaEntity();

        // 設定基礎欄位
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        entity.setProductId(domain.getProductId());
        // entity.setProduct(...) 由 Handler 在執行時設定（需要從 Repository 查詢）
        // entity.setProductVariant(...) 同上

        entity.setQuantity(domain.getQuantity());
        entity.setUnitPrice(domain.getUnitPrice().toBigDecimal());
        entity.setSubtotal(domain.getSubtotal().toBigDecimal());
        entity.setNotes(domain.getNotes());

        // 設定商品快照
        entity.setProductSnapshot(domain.getProductSnapshot());

        // 選項集合（若在 Handler 中設定，此處可跳過）
        // entity.setOptions(...) 由 Handler 在執行時設定

        return entity;
    }
}
