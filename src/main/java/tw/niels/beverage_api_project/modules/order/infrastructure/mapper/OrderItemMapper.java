package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import jakarta.persistence.EntityManager;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * OrderItem 映射工具
 *
 * 負責 OrderItemEntity 與 OrderItem Domain Entity 的雙向轉換。
 * OrderItem 是 Order Aggregate 內部的 Entity，生命週期由 Order 管理。
 *
 * 映射規則：
 * - JPA entity.getProduct().getId() → Domain productId
 * - JPA entity.getProductVariant().getId() → Domain variantId
 * - JPA entity.getOptions() ↔ Domain optionIds（寫入 order_item_options）
 * - JPA vo.ProductSnapshot ↔ Domain domain.model.ProductSnapshot 委派 OrderMapper 轉換
 *
 * 修改說明：
 * - toDomain 改用 OrderItem.reconstitute()（不驗證不變量），舊訂單（無快照）也能讀出；
 *   同時移除以反射呼叫 setId() 與 System.err 的作法。
 * - 寫入時所有關聯（商品、規格、選項）改用 EntityManager.getReference()，
 *   不再 new XxxEntity() 再 setId()（那會產生「新」實體，造成 flush 時的額外查詢或錯誤）。
 * - 補上選項（order_item_options）的寫入，原本完全沒有寫入。
 * ============================================================
 */
public class OrderItemMapper {

    private OrderItemMapper() {
    }

    /**
     * JPA OrderItem Entity → Domain OrderItem（讀取資料庫）
     *
     * @param entity JPA OrderItem Entity
     * @return Domain OrderItem，若 entity 為 null 返回 null
     */
    public static OrderItem toDomain(OrderItemEntity entity) {
        if (entity == null)
            return null;

        List<Long> optionIds = entity.getOptions() != null
                ? entity.getOptions().stream()
                        .map(ProductOptionEntity::getId)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        // 使用 reconstitute：不驗證快照等不變量，並直接還原品項 ID（取代反射 setId）
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getProduct() != null ? entity.getProduct().getId() : null,
                entity.getProductVariant() != null ? entity.getProductVariant().getId() : null,
                entity.getQuantity() != null ? entity.getQuantity() : 0,
                entity.getUnitPrice() != null ? Money.of(entity.getUnitPrice()) : Money.ZERO,
                entity.getSubtotal() != null ? Money.of(entity.getSubtotal()) : null,
                entity.getNotes(),
                optionIds,
                OrderMapper.toProductSnapshotDomain(entity.getProductSnapshot()));
    }

    /**
     * Domain OrderItem → 新的 JPA OrderItem Entity（新增品項）
     *
     * @param domain Domain OrderItem
     * @param order  所屬的 OrderEntity（受管或即將 persist）
     * @param em     EntityManager，用於取得關聯參照
     * @return 新的 OrderItemEntity（ID 由 BaseTsidEntity 自動產生）
     */
    public static OrderItemEntity toNewEntity(OrderItem domain, OrderEntity order, EntityManager em) {
        if (domain == null)
            return null;

        OrderItemEntity entity = new OrderItemEntity();
        // 若 Domain 已有 ID（理論上僅重建的品項會有），沿用之
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrder(order);
        applyTo(entity, domain, em);
        return entity;
    }

    /**
     * 將 Domain OrderItem 的狀態套用到既有的 JPA OrderItem Entity（新增與更新共用）。
     *
     * 關聯使用 EntityManager.getReference()：若同交易中已載入（OrderItemFactory 已批次查詢），
     * 會直接回傳受管實體，不會產生額外 SQL。
     *
     * @param entity 目標 Entity
     * @param domain 來源 Domain OrderItem
     * @param em     EntityManager
     */
    public static void applyTo(OrderItemEntity entity, OrderItem domain, EntityManager em) {
        // 商品關聯（只在不同時才更新，避免無謂的 dirty 標記）
        if (domain.getProductId() != null
                && (entity.getProduct() == null || !Objects.equals(entity.getProduct().getId(), domain.getProductId()))) {
            entity.setProduct(em.getReference(ProductEntity.class, domain.getProductId()));
        }

        // 規格關聯
        if (domain.getVariantId() != null
                && (entity.getProductVariant() == null
                    || !Objects.equals(entity.getProductVariant().getId(), domain.getVariantId()))) {
            entity.setProductVariant(em.getReference(ProductVariantEntity.class, domain.getVariantId()));
        }

        entity.setQuantity(domain.getQuantity());
        // Money VO 轉 BigDecimal 使用 .amount() 方法（Money 是 Java record）
        entity.setUnitPrice(domain.getUnitPrice() != null ? domain.getUnitPrice().amount() : BigDecimal.ZERO);
        entity.setSubtotal(domain.getSubtotal() != null ? domain.getSubtotal().amount() : BigDecimal.ZERO);
        entity.setNotes(domain.getNotes());

        // domain.model.ProductSnapshot → vo.ProductSnapshot（委派 OrderMapper 轉換）
        // 舊資料沒有快照時（domain 為 null）保留資料庫原值，不覆寫為 null
        if (domain.getProductSnapshot() != null) {
            entity.setProductSnapshot(OrderMapper.toProductSnapshotJpa(domain.getProductSnapshot()));
        }

        // 新增：同步選項集合（order_item_options），原本完全沒有寫入
        syncOptions(entity, domain.getOptionIds(), em);
    }

    /**
     * 同步品項的選項集合：移除不再選取的、加入新選取的。
     * 以 ID 比對，避免直接替換整個集合造成 Hibernate 重建整張關聯表。
     */
    private static void syncOptions(OrderItemEntity entity, List<Long> optionIds, EntityManager em) {
        Set<Long> targetIds = optionIds != null ? new HashSet<>(optionIds) : Collections.emptySet();
        if (entity.getOptions() == null) {
            entity.setOptions(new HashSet<>());
        }
        Set<ProductOptionEntity> current = entity.getOptions();

        // 移除不在目標清單中的選項
        current.removeIf(opt -> !targetIds.contains(opt.getId()));

        // 加入尚未存在的選項（以 getReference 取得參照）
        Set<Long> existingIds = current.stream().map(ProductOptionEntity::getId).collect(Collectors.toSet());
        for (Long optionId : targetIds) {
            if (!existingIds.contains(optionId)) {
                current.add(em.getReference(ProductOptionEntity.class, optionId));
            }
        }
    }
}
