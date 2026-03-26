package tw.niels.beverage_api_project.modules.order.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;

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
 * ============================================================
 */
public class OrderMapper {

    public static Order toDomain(OrderEntity jpaOrder) {
        if (jpaOrder == null) return null;

        var domainItems = jpaOrder.getItems() != null
            ? jpaOrder.getItems().stream()
                .map(OrderItemMapper::toDomain)
                .collect(Collectors.toList())
            : List.<tw.niels.beverage_api_project.modules.order.domain.model.OrderItem>of();

        OrderStatus domainStatus = jpaOrder.getStatus() != null
            ? OrderStatus.valueOf(jpaOrder.getStatus().name())
            : OrderStatus.PENDING;

        MemberSnapshot domainMemberSnapshot = toMemberSnapshotDomain(jpaOrder.getMemberSnapshot());

        return Order.reconstitute(
            jpaOrder.getId(),
            jpaOrder.getBrand()  != null ? jpaOrder.getBrand().getId()  : null,
            jpaOrder.getStore()  != null ? jpaOrder.getStore().getId()  : null,
            jpaOrder.getStaff()  != null ? jpaOrder.getStaff().getId()  : null,
            jpaOrder.getMember() != null ? jpaOrder.getMember().getId() : null,
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

    public static OrderEntity toEntity(Order domain) {
        if (domain == null) return null;

        OrderEntity jpaOrder = new OrderEntity();

        if (domain.getId() != null) {
            jpaOrder.setId(domain.getId());
        }

        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpaOrder.setBrand(brand);
        }
        if (domain.getStoreId() != null) {
            var store = new tw.niels.beverage_api_project.modules.store.entity.StoreEntity();
            store.setId(domain.getStoreId());
            jpaOrder.setStore(store);
        }
        if (domain.getStaffId() != null) {
            var staff = new tw.niels.beverage_api_project.modules.user.entity.UserEntity();
            staff.setId(domain.getStaffId());
            jpaOrder.setStaff(staff);
        }
        if (domain.getMemberId() != null) {
            var member = new tw.niels.beverage_api_project.modules.user.entity.UserEntity();
            member.setId(domain.getMemberId());
            jpaOrder.setMember(member);
        }

        jpaOrder.setOrderNumber(domain.getOrderNumber().value());

        if (domain.getStatus() != null) {
            jpaOrder.setStatus(
                tw.niels.beverage_api_project.modules.order.enums.OrderStatus.valueOf(
                    domain.getStatus().name()));
        }

        jpaOrder.setTotalAmount(domain.getTotalAmount().amount());
        jpaOrder.setDiscountAmount(domain.getDiscountAmount().amount());
        jpaOrder.setFinalAmount(domain.getFinalAmount().amount());
        jpaOrder.setPointsUsed(domain.getPointsUsed()   != null ? domain.getPointsUsed()   : 0L);
        jpaOrder.setPointsEarned(domain.getPointsEarned() != null ? domain.getPointsEarned() : 0L);
        jpaOrder.setCustomerNote(domain.getCustomerNote());

        if (domain.getCompletedTime() != null) {
            jpaOrder.setCompletedTime(Timestamp.from(domain.getCompletedTime()));
        }

        jpaOrder.setMemberSnapshot(toMemberSnapshotJpa(domain.getMemberSnapshot()));

        if (domain.getItems() != null && !domain.getItems().isEmpty()) {
            var jpaItems = domain.getItems().stream()
                .map(item -> {
                    var jpaItem = OrderItemMapper.toEntity(item);
                    jpaItem.setOrder(jpaOrder);
                    return jpaItem;
                })
                .collect(Collectors.toSet());
            jpaOrder.setItems(jpaItems);
        }

        return jpaOrder;
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
