package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import jakarta.persistence.EntityManager;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Order BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 修改說明（修正聚合更新必失敗的問題）：
 * - 原本 toEntity() 每次都 new OrderEntity() 再 setId(既有 ID)；BaseTsidEntity.isNew() 為 true，
 *   save() 走 persist()，而同交易已載入同 ID 的受管實體 → NonUniqueObjectException。
 * - 現在拆成兩條路徑：
 *   1. toNewEntity()：新訂單建立新的 OrderEntity；
 *   2. applyTo()：既有訂單把 Domain 狀態套用到同交易中「受管」的 OrderEntity，
 *      品項依 ID 同步（更新、刪除、新增，搭配 orphanRemoval）。
 * - 關聯一律使用 EntityManager.getReference()（品牌、店家、員工、會員、付款方式）。
 * - 補上 paymentMethod 的寫入（原本沒有寫入，付款統計全為 null）。
 * ============================================================
 */
public class OrderMapper {

    private OrderMapper() {
    }

    public static Order toDomain(OrderEntity jpaOrder) {
        if (jpaOrder == null) return null;

        List<OrderItem> domainItems = jpaOrder.getItems() != null
            ? jpaOrder.getItems().stream()
                .map(OrderItemMapper::toDomain)
                .collect(Collectors.toList())
            : List.of();

        OrderStatus domainStatus = jpaOrder.getStatus() != null
            ? OrderStatus.valueOf(jpaOrder.getStatus().name())
            : OrderStatus.PENDING;

        MemberSnapshot domainMemberSnapshot = toMemberSnapshotDomain(jpaOrder.getMemberSnapshot());

        // 使用含 guestRef / requestId 的完整 reconstitute 多載
        return Order.reconstitute(
            jpaOrder.getId(),
            jpaOrder.getBrand()  != null ? jpaOrder.getBrand().getId()  : null,
            jpaOrder.getStore()  != null ? jpaOrder.getStore().getId()  : null,
            jpaOrder.getStaff()  != null ? jpaOrder.getStaff().getId()  : null,
            jpaOrder.getMember() != null ? jpaOrder.getMember().getId() : null,
            jpaOrder.getGuestRef(),
            jpaOrder.getRequestId(),
            OrderNumber.of(jpaOrder.getOrderNumber()),
            domainStatus,
            jpaOrder.getTotalAmount()    != null ? Money.of(jpaOrder.getTotalAmount())    : Money.ZERO,
            jpaOrder.getDiscountAmount() != null ? Money.of(jpaOrder.getDiscountAmount()) : Money.ZERO,
            jpaOrder.getFinalAmount()    != null ? Money.of(jpaOrder.getFinalAmount())    : Money.ZERO,
            jpaOrder.getPointsUsed()    != null ? jpaOrder.getPointsUsed()    : 0L,
            jpaOrder.getPointsEarned()  != null ? jpaOrder.getPointsEarned()  : 0L,
            jpaOrder.getPaymentMethod() != null
                ? jpaOrder.getPaymentMethod().getPaymentMethodId().longValue()
                : null,
            jpaOrder.getCustomerNote(),
            jpaOrder.getOrderTime()     != null ? jpaOrder.getOrderTime().toInstant()     : null,
            jpaOrder.getCompletedTime() != null ? jpaOrder.getCompletedTime().toInstant() : null,
            domainItems,
            domainMemberSnapshot
        );
    }

    /**
     * Domain Order → 新的 OrderEntity（僅用於新訂單）。
     *
     * @param domain 新建立的 Order（isNew() 為 true）
     * @param em     EntityManager，用於取得關聯參照
     * @return 待 persist 的新 OrderEntity（ID 與 Domain 相同）
     */
    public static OrderEntity toNewEntity(Order domain, EntityManager em) {
        OrderEntity jpaOrder = new OrderEntity();
        // 使用 Domain 預先指派的 ID（覆寫 BaseTsidEntity 建構時自動產生的 TSID）
        jpaOrder.setId(domain.getId());

        // 品牌、店家、員工於建立後不再變動，只在新增時設定
        jpaOrder.setBrand(em.getReference(BrandEntity.class, domain.getBrandId()));
        jpaOrder.setStore(em.getReference(StoreEntity.class, domain.getStoreId()));
        if (domain.getStaffId() != null) {
            jpaOrder.setStaff(em.getReference(UserEntity.class, domain.getStaffId()));
        }
        jpaOrder.setOrderNumber(domain.getOrderNumber().value());
        jpaOrder.setGuestRef(domain.getGuestRef());
        jpaOrder.setRequestId(domain.getRequestId());
        // 下單時間以 Domain 為準（@CreationTimestamp 仍會於 INSERT 時給值）
        if (domain.getOrderTime() != null) {
            jpaOrder.setOrderTime(Date.from(domain.getOrderTime()));
        }

        applyTo(jpaOrder, domain, em);
        return jpaOrder;
    }

    /**
     * 將 Domain Order 的可變狀態套用到 OrderEntity（新增與更新共用）。
     *
     * 更新既有訂單時，target 必須是同交易中受管的實體；
     * 不呼叫 save()，由 Hibernate dirty checking 在 flush 時寫回，@Version 樂觀鎖同時生效。
     *
     * @param target 目標 OrderEntity（受管或即將 persist）
     * @param source 來源 Domain Order
     * @param em     EntityManager
     */
    public static void applyTo(OrderEntity target, Order source, EntityManager em) {
        // ─── 純量欄位 ────────────────────────────────────────────────
        if (source.getStatus() != null) {
            target.setStatus(
                tw.niels.beverage_api_project.modules.order.enums.OrderStatus.valueOf(source.getStatus().name()));
        }
        target.setTotalAmount(source.getTotalAmount().amount());
        target.setDiscountAmount(source.getDiscountAmount().amount());
        target.setFinalAmount(source.getFinalAmount().amount());
        target.setPointsUsed(source.getPointsUsed()   != null ? source.getPointsUsed()   : 0L);
        target.setPointsEarned(source.getPointsEarned() != null ? source.getPointsEarned() : 0L);
        target.setCustomerNote(source.getCustomerNote());
        // 完成時間：只在實際變動時寫入，避免 Date/Timestamp 型別差異造成不必要的 UPDATE
        if (source.getCompletedTime() == null) {
            target.setCompletedTime(null);
        } else if (target.getCompletedTime() == null
                || target.getCompletedTime().getTime() != source.getCompletedTime().toEpochMilli()) {
            target.setCompletedTime(Timestamp.from(source.getCompletedTime()));
        }
        target.setMemberSnapshot(toMemberSnapshotJpa(source.getMemberSnapshot()));

        // ─── 會員關聯（結帳時才綁定，只在有變動時更新）────────────────────
        Long currentMemberId = target.getMember() != null ? target.getMember().getId() : null;
        if (!Objects.equals(currentMemberId, source.getMemberId())) {
            target.setMember(source.getMemberId() != null
                ? em.getReference(UserEntity.class, source.getMemberId())
                : null);
        }

        // ─── 付款方式（新增：原本完全沒有寫入）─────────────────────────────
        Integer currentPmId = target.getPaymentMethod() != null ? target.getPaymentMethod().getPaymentMethodId() : null;
        Integer newPmId = source.getPaymentMethodId() != null ? source.getPaymentMethodId().intValue() : null;
        if (!Objects.equals(currentPmId, newPmId)) {
            target.setPaymentMethod(newPmId != null ? em.getReference(PaymentMethodEntity.class, newPmId) : null);
        }

        // ─── 品項同步（依 ID 比對：更新相同 ID、刪除不存在者、新增無 ID 者）────
        syncItems(target, source.getItems(), em);
    }

    /**
     * 同步訂單品項：
     * - Domain 有、Entity 也有（同 ID）→ 更新
     * - Entity 有、Domain 沒有 → 從集合移除（orphanRemoval=true 會刪除資料列）
     * - Domain 沒有 ID 或找不到對應 → 新增
     */
    private static void syncItems(OrderEntity target, List<OrderItem> domainItems, EntityManager em) {
        if (target.getItems() == null) {
            target.setItems(new HashSet<>());
        }
        Set<OrderItemEntity> currentItems = target.getItems();

        Map<Long, OrderItemEntity> existingById = new HashMap<>();
        for (OrderItemEntity e : currentItems) {
            existingById.put(e.getId(), e);
        }

        Set<Long> keepIds = domainItems.stream()
            .map(OrderItem::getId)
            .filter(Objects::nonNull)
            .filter(existingById::containsKey)
            .collect(Collectors.toSet());

        // 1. 移除 Domain 中已不存在的品項（orphanRemoval 負責刪除）
        currentItems.removeIf(e -> !keepIds.contains(e.getId()));

        // 2. 更新既有品項 / 新增品項
        for (OrderItem item : domainItems) {
            if (item.getId() != null && keepIds.contains(item.getId())) {
                OrderItemMapper.applyTo(existingById.get(item.getId()), item, em);
            } else {
                currentItems.add(OrderItemMapper.toNewEntity(item, target, em));
            }
        }
    }

    private static MemberSnapshot toMemberSnapshotDomain(
            tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot jpa) {
        if (jpa == null) return null;
        return new MemberSnapshot(jpa.userId(), jpa.fullName(), jpa.phone(), jpa.email());
    }

    private static tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot toMemberSnapshotJpa(
            MemberSnapshot domain) {
        if (domain == null) return null;
        return new tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot(
            domain.userId(), domain.fullName(), domain.phone(), domain.email());
    }

    static ProductSnapshot toProductSnapshotDomain(
            tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot jpa) {
        if (jpa == null) return null;
        return new ProductSnapshot(
            jpa.productId(), jpa.name(),
            jpa.basePrice() != null ? Money.of(jpa.basePrice()) : Money.ZERO,
            jpa.categoryName(), jpa.variantName());
    }

    static tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot toProductSnapshotJpa(
            ProductSnapshot domain) {
        if (domain == null) return null;
        return new tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot(
            domain.productId(), domain.name(),
            domain.basePrice() != null ? domain.basePrice().amount() : null,
            domain.categoryName(), domain.variantName());
    }
}
