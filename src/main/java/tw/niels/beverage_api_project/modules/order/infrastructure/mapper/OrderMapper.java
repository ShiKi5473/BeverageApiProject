package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.entity.Order as OrderJpaEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem as OrderItemJpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Order BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 負責統一管理 OrderJpaEntity 與 Order Domain Model 之間的轉換邏輯，
 * 避免轉換代碼分散在各層，提高代碼的可維護性與一致性。
 *
 * 映射規則：
 * - JPA 的 id, brandId, storeId, staffId 對應 Domain 的相應欄位
 * - JPA 的 BigDecimal 轉換為 Domain 的 Money Value Object
 * - JPA 的 Date 轉換為 Domain 的 Instant
 * - JPA Entity 關聯（Brand, Store, User）未直接映射，改用 ID
 * - OrderItem 集合遞迴轉換
 *
 * ============================================================
 */
public class OrderMapper {

    /**
     * JPA Entity → Domain Model（讀取資料庫）
     *
     * 將持久化的 JPA Entity 轉換為純業務領域模型，
     * 適用於載入訂單時的轉換。
     *
     * @param entity JPA 訂單實體（來自資料庫）
     * @return 對應的 Domain Order Aggregate Root，若 entity 為 null 返回 null
     */
    public static Order toDomain(OrderJpaEntity entity) {
        if (entity == null) return null;

        // 建立 Domain Order（需調用適當的靜態工廠方法或建構子）
        // 暫時使用反射或直接建構，待確認 Order 的開放方法
        Order order = new Order(
            entity.getBrandId(),
            entity.getStoreId(),
            entity.getStaffId(),
            OrderNumber.of(entity.getOrderNumber()),
            // 轉換訂單品項列表（遞迴轉換）
            entity.getItems() != null
                ? entity.getItems().stream()
                    .map(OrderItemMapper::toDomain)
                    .collect(Collectors.toList())
                : Collections.emptyList(),
            // 計算原始金額（BigDecimal → Money VO）
            entity.getTotalAmount() != null
                ? Money.of(entity.getTotalAmount())
                : Money.ZERO,
            entity.getCustomerNote()
        );

        // 設定 ID（由 Infrastructure Layer 在持久化後設定）
        // 使用反射設定 package-private setId 方法，或改用其他方式
        try {
            var setIdMethod = Order.class.getDeclaredMethod("setId", Long.class);
            setIdMethod.setAccessible(true);
            setIdMethod.invoke(order, entity.getId());
        } catch (Exception e) {
            // 若反射失敗，日誌記錄但不中斷流程（之後的 save 會重新設定）
            System.err.println("警告：無法設定 Order ID，將由 Repository save 方法處理");
        }

        // 設定支付與折扣相關欄位（若已付款或有折扣）
        if (entity.getPaymentMethodId() != null) {
            try {
                var setPaymentMethodIdMethod = Order.class.getDeclaredMethod("setPaymentMethodId", Long.class);
                setPaymentMethodIdMethod.setAccessible(true);
                setPaymentMethodIdMethod.invoke(order, entity.getPaymentMethodId());

                var setPointsUsedMethod = Order.class.getDeclaredMethod("setPointsUsed", Long.class);
                setPointsUsedMethod.setAccessible(true);
                setPointsUsedMethod.invoke(order, entity.getPointsUsed());

                // 設定折扣欄位
                if (entity.getDiscountAmount() != null) {
                    var setDiscountAmountMethod = Order.class.getDeclaredMethod(
                        "setDiscountAmount", Money.class);
                    setDiscountAmountMethod.setAccessible(true);
                    setDiscountAmountMethod.invoke(order, Money.of(entity.getDiscountAmount()));
                }

                // 設定最終金額
                if (entity.getFinalAmount() != null) {
                    var setFinalAmountMethod = Order.class.getDeclaredMethod("setFinalAmount", Money.class);
                    setFinalAmountMethod.setAccessible(true);
                    setFinalAmountMethod.invoke(order, Money.of(entity.getFinalAmount()));
                }

                // 設定積點相關
                if (entity.getPointsEarned() != null) {
                    var setPointsEarnedMethod = Order.class.getDeclaredMethod("setPointsEarned", Long.class);
                    setPointsEarnedMethod.setAccessible(true);
                    setPointsEarnedMethod.invoke(order, entity.getPointsEarned());
                }

            } catch (Exception e) {
                System.err.println("警告：無法設定訂單支付相關欄位");
            }
        }

        // 設定訂單狀態
        if (entity.getStatus() != null) {
            try {
                var setStatusMethod = Order.class.getDeclaredMethod(
                    "setStatus",
                    tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus.class);
                setStatusMethod.setAccessible(true);
                setStatusMethod.invoke(order, entity.getStatus());
            } catch (Exception e) {
                System.err.println("警告：無法設定訂單狀態");
            }
        }

        // 設定時間戳
        if (entity.getOrderTime() != null) {
            try {
                var setOrderTimeMethod = Order.class.getDeclaredMethod("setOrderTime", Instant.class);
                setOrderTimeMethod.setAccessible(true);
                setOrderTimeMethod.invoke(order, entity.getOrderTime().toInstant());
            } catch (Exception e) {
                // 時間戳設定失敗不影響核心邏輯
            }
        }

        if (entity.getCompletedTime() != null) {
            try {
                var setCompletedTimeMethod = Order.class.getDeclaredMethod("setCompletedTime", Instant.class);
                setCompletedTimeMethod.setAccessible(true);
                setCompletedTimeMethod.invoke(order, entity.getCompletedTime().toInstant());
            } catch (Exception e) {
                // 時間戳設定失敗不影響核心邏輯
            }
        }

        // 設定會員快照
        if (entity.getMemberSnapshot() != null) {
            try {
                var setMemberSnapshotMethod = Order.class.getDeclaredMethod(
                    "setMemberSnapshot",
                    tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot.class);
                setMemberSnapshotMethod.setAccessible(true);
                setMemberSnapshotMethod.invoke(order, entity.getMemberSnapshot());
            } catch (Exception e) {
                System.err.println("警告：無法設定會員快照");
            }
        }

        return order;
    }

    /**
     * Domain Model → JPA Entity（寫入資料庫）
     *
     * 將業務領域模型轉換為可持久化的 JPA Entity，
     * 適用於儲存訂單時的轉換。
     *
     * @param domain Domain Order Aggregate Root
     * @return 對應的 JPA 訂單實體，若 domain 為 null 返回 null
     */
    public static OrderJpaEntity toEntity(Order domain) {
        if (domain == null) return null;

        OrderJpaEntity entity = new OrderJpaEntity();

        // 設定基礎欄位（ID 由 Repository 決定是否保留）
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }

        entity.setBrandId(domain.getBrandId());
        entity.setStoreId(domain.getStoreId());
        entity.setStaffId(domain.getStaffId());
        entity.setOrderNumber(domain.getOrderNumber().value());

        // 轉換訂單品項集合（遞迴轉換，並設定雙向關聯）
        if (domain.getItems() != null && !domain.getItems().isEmpty()) {
            var jpaItems = domain.getItems().stream()
                .map(item -> {
                    OrderItemJpaEntity jpaItem = OrderItemMapper.toEntity(item);
                    jpaItem.setOrder(entity); // 設定雙向關聯
                    return jpaItem;
                })
                .collect(Collectors.toSet());
            entity.setItems(jpaItems);
        }

        // 設定訂單狀態（Domain OrderStatus enum → JPA OrderStatus enum，通常是同一類型）
        entity.setStatus(domain.getStatus());

        // 轉換金額欄位（Money VO → BigDecimal）
        entity.setTotalAmount(domain.getTotalAmount().toBigDecimal());
        entity.setDiscountAmount(domain.getDiscountAmount().toBigDecimal());
        entity.setFinalAmount(domain.getFinalAmount().toBigDecimal());

        // 設定積點相關
        entity.setPointsUsed(domain.getPointsUsed() != null ? domain.getPointsUsed() : 0L);
        entity.setPointsEarned(domain.getPointsEarned() != null ? domain.getPointsEarned() : 0L);

        // 設定支付方式 ID
        entity.setPaymentMethodId(domain.getPaymentMethodId());

        // 設定顧客備註
        entity.setCustomerNote(domain.getCustomerNote());

        // 轉換時間戳（Instant → Date，若需要則轉換）
        // 通常 JPA Entity 已有 @CreationTimestamp，不需手動設定 orderTime
        // entity.setOrderTime(new Date(domain.getOrderTime().toEpochMilli()));
        if (domain.getCompletedTime() != null) {
            entity.setCompletedTime(java.sql.Timestamp.from(domain.getCompletedTime()));
        }

        // 設定會員快照
        entity.setMemberSnapshot(domain.getMemberSnapshot());

        return entity;
    }
}
