package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigRepository;
import tw.niels.beverage_api_project.modules.order.application.command.ProcessPaymentCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.domain.service.OrderPricingDomainService;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;

/**
 * 訂單結帳付款 Handler（針對已存在的 PENDING/HELD 訂單）
 *
 * 處理已建立但尚未付款的訂單的結帳流程。
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate
 * 2. 驗證付款方式
 * 3. 若有會員，建立 MemberSnapshot
 * 4. 計算積點折扣（透過 OrderPricingDomainService）
 * 5. 呼叫 order.processPayment() 執行付款（狀態 → PREPARING）
 * 6. 儲存 Aggregate
 * 7. 發佈領域事件
 *
 * 發佈事件：OrderStatusChangedEvent（PENDING/HELD → PREPARING）
 *
 * 已知待優化（C3）：積點扣減目前在事務內執行，應移至 AFTER_COMMIT 事件監聽。
 */
@Component
public class ProcessPaymentHandler {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final PaymentMethodService paymentMethodService;
    private final PromotionService promotionService;
    private final BrandPointConfigRepository brandPointConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderPricingDomainService orderPricingDomainService = new OrderPricingDomainService();

    public ProcessPaymentHandler(OrderRepository orderRepository,
                                UserService userService,
                                PaymentMethodService paymentMethodService,
                                PromotionService promotionService,
                                BrandPointConfigRepository brandPointConfigRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.paymentMethodService = paymentMethodService;
        this.promotionService = promotionService;
        this.brandPointConfigRepository = brandPointConfigRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(ProcessPaymentCommand command) {
        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        PaymentMethodEntity paymentMethod = paymentMethodService.getByCode(command.paymentMethod());

        // 處理會員快照
        User memberUser = null;
        MemberSnapshot memberSnapshot = null;
        if (command.memberId() != null) {
            memberUser = userService.getMember(command.brandId(), command.memberId());
            memberSnapshot = new MemberSnapshot(
                memberUser.getId(),
                memberUser.getMemberProfile().getFullName(),
                memberUser.getPrimaryPhone(),
                memberUser.getMemberProfile().getEmail()
            );
        }

        // 計算折扣
        Money pointsDiscount = Money.ZERO;
        if (command.memberId() != null && command.pointsToUse() > 0) {
            BigDecimal redeemRate = getRedeemRate(command.brandId());
            pointsDiscount = orderPricingDomainService.calculateRedeemAmount(command.pointsToUse(), redeemRate);
        }

        // TODO: PromotionService 整合
        Money totalDiscount = pointsDiscount;

        // 執行付款邏輯
        order.processPayment(
            paymentMethod.getPaymentMethodId().longValue(),
            totalDiscount,
            command.pointsToUse(),
            command.memberId(),
            memberSnapshot
        );

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }

    private BigDecimal getRedeemRate(Long brandId) {
        return brandPointConfigRepository.findById(brandId)
            .map(BrandPointConfig::getRedeemRate)
            .orElse(new BigDecimal("0.1"));
    }
}
