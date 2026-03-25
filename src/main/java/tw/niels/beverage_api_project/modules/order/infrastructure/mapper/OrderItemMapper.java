package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;

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
 * 映射規則：
 * - JPA entity.getProduct().getId() → Domain productId（product 關聯非 null）
 * - JPA entity.getProductVariant().getId() → Domain variantId
 * - JPA vo.ProductSnapshot ↔ Domain domain.model.ProductSnapshot 委派 OrderMapper 轉換
 * - Money.of(BigDecimal) 轉換 JPA BigDecimal → Domain Money VO
 * - money.amount() 轉換 Domain Money VO → JPA BigDecimal
 * - toEntity() 透過 Proxy 物件設定 Product / ProductVariant 關聯（僅帶入 ID）
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
    public static OrderItem toDomain(tw.niels.beverage_api_project.modules.order.entity.OrderItem entity) {
        if (entity == null) return null;

        // 使用 Domain OrderItem 的靜態工廠方法建立
        OrderItem item = OrderItem.create(
            // JPA entity 使用關聯欄位，需取出 ID
            entity.getProduct() != null ? entity.getProduct().getId() : null,
            entity.getProductVariant() != null ? entity.getProductVariant().getId() : null,
            entity.getQuantity(),
            Money.of(entity.getUnitPrice()),
            entity.getNotes(),
            // 轉換選項 ID 集合（ProductOption 繼承 BaseTsidEntity，有 getId()）
            entity.getOptions() != null
                ? entity.getOptions().stream()
                    .map(opt -> opt.getId())
                    .collect(Collectors.toList())
                : Collections.emptyList(),
            // vo.ProductSnapshot → domain.model.ProductSnapshot（委派 OrderMapper 轉換）
            OrderMapper.toProductSnapshotDomain(entity.getProductSnapshot())
        );

        // 設定 OrderItem 的 ID（由 Infrastructure 設定，setId 是 package-private，使用反射）
        if (entity.getId() != null) {
            try {
                var setIdMethod = OrderItem.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(item, entity.getId());
            } catch (NoSuchMethodException | IllegalAccessException |
                     java.lang.reflect.InvocationTargetException | SecurityException e) {
                // 若反射失敗，日誌記錄但不中斷
                System.err.println("警告：無法設定 OrderItem ID：" + e.getMessage());
            }
        }

        return item;
    }

    /**
     * Domain OrderItem → JPA OrderItem Entity（寫入資料庫）
     *
     * Product / ProductVariant 關聯透過 Proxy 物件僅帶入 ID，
     * 避免額外查詢，由 JPA 在 flush 時驗證外鍵存在。
     *
     * @param domain Domain OrderItem
     * @return JPA OrderItem Entity，若 domain 為 null 返回 null
     */
    public static tw.niels.beverage_api_project.modules.order.entity.OrderItem toEntity(OrderItem domain) {
        if (domain == null) return null;

        tw.niels.beverage_api_project.modules.order.entity.OrderItem entity =
            new tw.niels.beverage_api_project.modules.order.entity.OrderItem();

        // 設定 ID（若已有 ID 則為更新操作）
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        // 設定 Product 關聯（透過 Proxy 僅帶入 ID，避免額外查詢）
        if (domain.getProductId() != null) {
            var product = new tw.niels.beverage_api_project.modules.product.entity.Product();
            product.setId(domain.getProductId());
            entity.setProduct(product);
        }

        // 設定 ProductVariant 關聯（透過 Proxy 僅帶入 ID）
        if (domain.getVariantId() != null) {
            var variant = new tw.niels.beverage_api_project.modules.product.entity.ProductVariant();
            variant.setId(domain.getVariantId());
            entity.setProductVariant(variant);
        }

        entity.setQuantity(domain.getQuantity());
        // Money VO 轉 BigDecimal 使用 .amount() 方法（Money 是 Java record）
        entity.setUnitPrice(domain.getUnitPrice().amount());
        entity.setSubtotal(domain.getSubtotal().amount());
        entity.setNotes(domain.getNotes());

        // domain.model.ProductSnapshot → vo.ProductSnapshot（委派 OrderMapper 轉換）
        entity.setProductSnapshot(OrderMapper.toProductSnapshotJpa(domain.getProductSnapshot()));

        // Options（選項集合）由 Handler 在執行時另行設定，此處略過

        return entity;
    }
}
