package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigRepository;
import tw.niels.beverage_api_project.modules.order.application.command.PosCheckoutCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.*;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.domain.service.OrderPricingDomainService;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;
import tw.niels.beverage_api_project.modules.product.domain.model.ProductVariant;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * POS 快速結帳 Handler（一步到位：建立 + 付款）
 *
 * 將訂單建立與付款合併為單一操作，適用於 POS 現場結帳場景。
 *
 * 處理流程：
 * 1. 驗證店家、員工、付款方式
 * 2. 若有會員，查詢會員資料並建立 MemberSnapshot
 * 3. 將 OrderItemDto 轉為 Domain OrderItem（含 ProductSnapshot）
 * 4. 產生訂單號碼
 * 5. 呼叫 Order.place() 建立 Aggregate（初始狀態 PENDING）
 * 6. 計算積點折扣（透過 OrderPricingDomainService）與促銷折扣
 * 7. 呼叫 order.processPayment() 執行付款（狀態 PENDING → PREPARING）
 * 8. 透過 OrderRepository 儲存 Aggregate
 * 9. 發佈領域事件
 *
 * 發佈事件：OrderPlacedEvent（建立）、OrderStatusChangedEvent（PENDING → PREPARING）
 *
 * 已知待優化（C3）：積點扣減目前在事務內執行，應移至 AFTER_COMMIT 事件監聽。
 */
@Component
public class PosCheckoutHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderNumberService orderNumberService;
    private final StoreService storeService;
    private final UserService userService;
    private final PaymentMethodService paymentMethodService;
    private final PromotionService promotionService;
    private final BrandPointConfigRepository brandPointConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderPricingDomainService orderPricingDomainService = new OrderPricingDomainService();

    public PosCheckoutHandler(OrderRepository orderRepository,
                              ProductRepository productRepository,
                              OrderNumberService orderNumberService,
                              StoreService storeService,
                              UserService userService,
                              PaymentMethodService paymentMethodService,
                              PromotionService promotionService,
                              BrandPointConfigRepository brandPointConfigRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderNumberService = orderNumberService;
        this.storeService = storeService;
        this.userService = userService;
        this.paymentMethodService = paymentMethodService;
        this.promotionService = promotionService;
        this.brandPointConfigRepository = brandPointConfigRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long handle(PosCheckoutCommand command) {
        // 1. 驗證基礎資料
        storeService.getStore(command.brandId(), command.storeId());
        userService.getUser(command.brandId(), command.userId());
        PaymentMethodEntity paymentMethod = paymentMethodService.getByCode(command.paymentMethod());

        // 2. 處理成員與快照
        User memberUser = null;
        MemberSnapshot memberSnapshot = null;
        if (command.memberId() != null) {
            memberUser = userService.getMember(command.brandId(), command.memberId());
            if (memberUser.getMemberProfile() != null) {
                memberSnapshot = new MemberSnapshot(
                    memberUser.getId(),
                    memberUser.getMemberProfile().getFullName(),
                    memberUser.getPrimaryPhone(), // User 實體有 primaryPhone
                    memberUser.getMemberProfile().getEmail()
                );
            }
        }

        // 3. 處理訂單品項
        List<OrderItem> domainItems = processItems(command.brandId(), command.items());

        // 4. 產生訂單號碼
        long sequence = orderNumberService.getNextStoreDailySequence(command.storeId());
        String orderNumStr = String.format("A%03d", sequence);
        OrderNumber orderNumber = OrderNumber.of(orderNumStr);

        // 5. 建立訂單 (初始狀態為 PENDING)
        Order order = Order.place(
            command.brandId(),
            command.storeId(),
            command.userId(),
            orderNumber,
            domainItems,
            null
        );

        // 6. 計算折扣 (積點與促銷)
        Money pointsDiscount = Money.ZERO;
        if (command.memberId() != null && command.pointsToUse() > 0) {
            BigDecimal redeemRate = getRedeemRate(command.brandId());
            pointsDiscount = orderPricingDomainService.calculateRedeemAmount(command.pointsToUse(), redeemRate);
        }

        // 促銷折扣 (暫時傳回 0，直到 PromotionService 支援 Domain Model)
        Money promoDiscount = Money.ZERO; 
        // BigDecimal promoAmt = promotionService.calculateBestDiscount(jpaOrder);
        // promoDiscount = Money.of(promoAmt);

        Money totalDiscount = pointsDiscount.add(promoDiscount);

        // 7. 執行付款流程 (狀態轉為 PREPARING)
        order.processPayment(
            paymentMethod.getPaymentMethodId().longValue(),
            totalDiscount,
            command.pointsToUse(),
            command.memberId(),
            memberSnapshot
        );

        // 8. 儲存
        orderRepository.save(order);

        // 9. 發布領域事件
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return order.getId();
    }

    private BigDecimal getRedeemRate(Long brandId) {
        return brandPointConfigRepository.findById(brandId)
            .map(BrandPointConfig::getRedeemRate)
            .orElse(new BigDecimal("0.1"));
    }

    private List<OrderItem> processItems(Long brandId, List<OrderItemDto> itemDtos) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDto dto : itemDtos) {
            Product product = productRepository.findProductById(brandId, dto.productId())
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在: " + dto.productId()));
            
            ProductVariant variant = product.getVariants().stream()
                .filter(v -> v.getId().equals(dto.variantId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("規格不存在: " + dto.variantId()));

            ProductSnapshot snapshot = new ProductSnapshot(
                product.getId(),
                product.getName(),
                variant.getPrice(),
                "Category",
                variant.getName()
            );

            OrderItem item = OrderItem.create(
                product.getId(),
                variant.getId(),
                dto.quantity(),
                variant.getPrice(),
                dto.notes(),
                dto.optionIds() != null ? dto.optionIds() : List.of(),
                snapshot
            );
            items.add(item);
        }
        return items;
    }
}
