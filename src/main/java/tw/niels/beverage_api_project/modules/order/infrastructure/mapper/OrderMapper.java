package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Order BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 負責統一管理 JPA OrderEntity 與 Order Domain Model 之間的轉換邏輯，
 * 避免轉換代碼分散在各層，提高代碼的可維護性與一致性。
 *
 * 映射規則：
 * - JPA 的關聯欄位（brand, store, staff, member）取其 ID 對應 Domain 的 ID 欄位
 * - JPA 的 BigDecimal 轉換為 Domain 的 Money Value Object（Money.of(BigDecimal)）
 * - Money Value Object 轉回 BigDecimal 使用 .amount() 方法（Money 是 Java record）
 * - JPA 的 Date 轉換為 Domain 的 Instant（.toInstant()）
 * - JPA vo.MemberSnapshot ↔ Domain domain.model.MemberSnapshot 之間欄位名稱相同，直接複製
 * - JPA vo.ProductSnapshot 的 basePrice 是 BigDecimal，Domain 的是 Money，需轉換
 * - enums.OrderStatus ↔ domain.model.OrderStatus 透過 .name() 字串安全轉換
 * - OrderItem 集合由 OrderItemMapper 遞迴轉換
 *
 * 使用 Order.reconstitute() 重建 Domain Aggregate，不觸發任何 Domain Event。
 * entity.Order / entity.OrderItem 使用完整類別路徑，避免與 domain.model 同名衝突。
 *
 * ============================================================
 */
public class OrderMapper {

    /**
     * JPA Entity → Domain Model（讀取資料庫）
     *
     * 使用 Order.reconstitute() 靜態工廠方法重建 Domain Aggregate，
     * 不執行業務規則驗證，不發布 Domain Event。
     *
     * @param jpaOrder JPA 訂單實體（來自資料庫）
     * @return 對應的 Domain Order Aggregate Root，若 jpaOrder 為 null 返回 null
     */
    public static Order toDomain(tw.niels.beverage_api_project.modules.order.entity.Order jpaOrder) {
        if (jpaOrder == null) return null;

        // 轉換訂單品項集合（委派 OrderItemMapper 遞迴轉換）
        var domainItems = jpaOrder.getItems() != null
            ? jpaOrder.getItems().stream()
                .map(OrderItemMapper::toDomain)
                .collect(Collectors.toList())
            : List.<tw.niels.beverage_api_project.modules.order.domain.model.OrderItem>of();

        // 轉換 JPA enums.OrderStatus → Domain domain.model.OrderStatus（透過名稱映射）
        OrderStatus domainStatus = jpaOrder.getStatus() != null
            ? OrderStatus.valueOf(jpaOrder.getStatus().name())
            : OrderStatus.PENDING;

        // 轉換 JPA vo.MemberSnapshot → Domain domain.model.MemberSnapshot
        MemberSnapshot domainMemberSnapshot = toMemberSnapshotDomain(jpaOrder.getMemberSnapshot());

        // 使用 Order.reconstitute() 重建 Domain Aggregate（不觸發 Domain Event）
        return Order.reconstitute(
            jpaOrder.getId(),
            // JPA 使用 Entity 關聯，需取出 ID 欄位
            jpaOrder.getBrand()  != null ? jpaOrder.getBrand().getId()  : null,
            jpaOrder.getStore()  != null ? jpaOrder.getStore().getId()  : null,
            jpaOrder.getStaff()  != null ? jpaOrder.getStaff().getId()  : null,
            jpaOrder.getMember() != null ? jpaOrder.getMember().getId() : null,
            OrderNumber.of(jpaOrder.getOrderNumber()),
            domainStatus,
            // 轉換金額（BigDecimal → Money VO）
            jpaOrder.getTotalAmount()    != null ? Money.of(jpaOrder.getTotalAmount())    : Money.ZERO,
            jpaOrder.getDiscountAmount() != null ? Money.of(jpaOrder.getDiscountAmount()) : Money.ZERO,
            jpaOrder.getFinalAmount()    != null ? Money.of(jpaOrder.getFinalAmount())    : Money.ZERO,
            jpaOrder.getPointsUsed()    != null ? jpaOrder.getPointsUsed()    : 0L,
            jpaOrder.getPointsEarned()  != null ? jpaOrder.getPointsEarned()  : 0L,
            // PaymentMethodEntity → Long（取 ID）
            jpaOrder.getPaymentMethod() != null
                ? jpaOrder.getPaymentMethod().getPaymentMethodId().longValue()
                : null,
            jpaOrder.getCustomerNote(),
            // 轉換 Date → Instant
            jpaOrder.getOrderTime()     != null ? jpaOrder.getOrderTime().toInstant()     : null,
            jpaOrder.getCompletedTime() != null ? jpaOrder.getCompletedTime().toInstant() : null,
            domainItems,
            domainMemberSnapshot
        );
    }

    /**
     * Domain Model → JPA Entity（寫入資料庫）
     *
     * 將業務領域模型轉換為可持久化的 JPA Entity。
     * Brand / Store / Staff / Member 等關聯僅設定 ID（Proxy 物件），
     * 避免額外的查詢開銷，由 JPA 在 flush 時驗證外鍵是否存在。
     *
     * @param domain Domain Order Aggregate Root
     * @return 對應的 JPA 訂單實體，若 domain 為 null 返回 null
     */
    public static tw.niels.beverage_api_project.modules.order.entity.Order toEntity(Order domain) {
        if (domain == null) return null;

        tw.niels.beverage_api_project.modules.order.entity.Order jpaOrder =
            new tw.niels.beverage_api_project.modules.order.entity.Order();

        // 設定 ID（若已有 ID 則為更新操作，JPA 會執行 UPDATE）
        if (domain.getId() != null) {
            jpaOrder.setId(domain.getId());
        }

        // 設定 Brand / Store / Staff / Member（透過 Proxy 物件僅帶入 ID，節省查詢）
        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.Brand();
            brand.setId(domain.getBrandId());
            jpaOrder.setBrand(brand);
        }
        if (domain.getStoreId() != null) {
            var store = new tw.niels.beverage_api_project.modules.store.entity.Store();
            store.setId(domain.getStoreId());
            jpaOrder.setStore(store);
        }
        if (domain.getStaffId() != null) {
            var staff = new tw.niels.beverage_api_project.modules.user.entity.User();
            staff.setId(domain.getStaffId());
            jpaOrder.setStaff(staff);
        }
        if (domain.getMemberId() != null) {
            var member = new tw.niels.beverage_api_project.modules.user.entity.User();
            member.setId(domain.getMemberId());
            jpaOrder.setMember(member);
        }

        jpaOrder.setOrderNumber(domain.getOrderNumber().value());

        // 轉換 Domain domain.model.OrderStatus → JPA enums.OrderStatus（透過名稱映射）
        if (domain.getStatus() != null) {
            jpaOrder.setStatus(
                tw.niels.beverage_api_project.modules.order.enums.OrderStatus.valueOf(
                    domain.getStatus().name()));
        }

        // 轉換金額（Money VO → BigDecimal，使用 Money record 的 .amount() 方法）
        jpaOrder.setTotalAmount(domain.getTotalAmount().amount());
        jpaOrder.setDiscountAmount(domain.getDiscountAmount().amount());
        jpaOrder.setFinalAmount(domain.getFinalAmount().amount());

        // 設定積點相關
        jpaOrder.setPointsUsed(domain.getPointsUsed()   != null ? domain.getPointsUsed()   : 0L);
        jpaOrder.setPointsEarned(domain.getPointsEarned() != null ? domain.getPointsEarned() : 0L);

        // 設定顧客備註
        jpaOrder.setCustomerNote(domain.getCustomerNote());

        // 轉換完成時間（Instant → Timestamp，JPA 接受 java.sql.Timestamp）
        if (domain.getCompletedTime() != null) {
            jpaOrder.setCompletedTime(Timestamp.from(domain.getCompletedTime()));
        }

        // 轉換 Domain domain.model.MemberSnapshot → JPA vo.MemberSnapshot
        jpaOrder.setMemberSnapshot(toMemberSnapshotJpa(domain.getMemberSnapshot()));

        // 轉換訂單品項集合（委派 OrderItemMapper，並設定雙向關聯）
        if (domain.getItems() != null && !domain.getItems().isEmpty()) {
            var jpaItems = domain.getItems().stream()
                .map(item -> {
                    var jpaItem = OrderItemMapper.toEntity(item);
                    jpaItem.setOrder(jpaOrder); // 設定雙向關聯，JPA Cascade 需要
                    return jpaItem;
                })
                .collect(Collectors.toSet());
            jpaOrder.setItems(jpaItems);
        }

        return jpaOrder;
    }

    // ─── 快照類型轉換輔助方法 ──────────────────────────────────────────────

    /**
     * JPA vo.MemberSnapshot → Domain domain.model.MemberSnapshot
     * 兩個 record 欄位相同，直接重新建構。
     */
    private static MemberSnapshot toMemberSnapshotDomain(
            tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot jpa) {
        if (jpa == null) return null;
        return new MemberSnapshot(jpa.userId(), jpa.fullName(), jpa.phone(), jpa.email());
    }

    /**
     * Domain domain.model.MemberSnapshot → JPA vo.MemberSnapshot
     */
    private static tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot toMemberSnapshotJpa(
            MemberSnapshot domain) {
        if (domain == null) return null;
        return new tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot(
            domain.userId(), domain.fullName(), domain.phone(), domain.email());
    }

    /**
     * JPA vo.ProductSnapshot → Domain domain.model.ProductSnapshot
     * JPA 使用 BigDecimal basePrice，Domain 使用 Money basePrice。
     */
    static ProductSnapshot toProductSnapshotDomain(
            tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot jpa) {
        if (jpa == null) return null;
        return new ProductSnapshot(
            jpa.productId(), jpa.name(),
            jpa.basePrice() != null ? Money.of(jpa.basePrice()) : Money.ZERO,
            jpa.categoryName(), jpa.variantName());
    }

    /**
     * Domain domain.model.ProductSnapshot → JPA vo.ProductSnapshot
     */
    static tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot toProductSnapshotJpa(
            ProductSnapshot domain) {
        if (domain == null) return null;
        return new tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot(
            domain.productId(), domain.name(),
            domain.basePrice() != null ? domain.basePrice().amount() : null,
            domain.categoryName(), domain.variantName());
    }
}
