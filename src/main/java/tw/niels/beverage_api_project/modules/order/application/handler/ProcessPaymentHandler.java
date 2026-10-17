package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.ProcessPaymentCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;

/**
 * 訂單結帳付款 Handler（針對已存在的 PENDING/HELD 訂單）
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate，並驗證訂單屬於呼叫者可操作的分店（StoreScope，不符 → 403）
 * 2. 驗證付款方式；若有會員，驗證會員（存在且未停用）並建立 MemberSnapshot
 * 3. 以 OrderCheckoutService 計算促銷折扣與點數折抵
 * 4. 於同一交易內同步扣點（不足 → DomainException 422，整筆回滾）
 * 5. 呼叫 order.processPayment()（狀態 → PREPARING）
 * 6. 儲存並發佈領域事件
 *
 * 發佈事件：OrderStatusChangedEvent（PENDING/HELD → PREPARING）
 *
 * 修改說明：
 * - 促銷折扣改為實際呼叫 PromotionService（原本 TODO，B-3）；
 * - 點數改為交易內同步扣除，取代 AFTER_COMMIT 的 PointDeductionEventHandler（B-1）；
 * - 移除過時的「C3：應移至 AFTER_COMMIT」註記（已確認改為同步扣點）；
 * - 新增分店存取檢查（M-1）：原本只依品牌載入訂單，綁店員工可結帳其他分店的訂單；
 * - 停用會員不可綁定訂單或折抵點數（m-5，由 OrderCheckoutService 檢查，422）。
 */
@Component
public class ProcessPaymentHandler {

    private final OrderRepository orderRepository;
    private final OrderCheckoutService orderCheckoutService;
    private final PaymentMethodService paymentMethodService;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessPaymentHandler(OrderRepository orderRepository,
                                 OrderCheckoutService orderCheckoutService,
                                 PaymentMethodService paymentMethodService,
                                 ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderCheckoutService = orderCheckoutService;
        this.paymentMethodService = paymentMethodService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(ProcessPaymentCommand command) {
        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 新增（M-1）：綁店員工只能結帳自己分店的訂單；在交易內、扣點與付款之前檢查，不符回 403
        command.storeScope().verifyAccess(order.getStoreId());

        PaymentMethodEntity paymentMethod = paymentMethodService.getByCode(command.paymentMethod());

        // 會員快照（同時驗證會員存在且屬於本品牌）
        MemberSnapshot memberSnapshot = orderCheckoutService.resolveMemberSnapshot(command.brandId(), command.memberId());

        // 計算折扣（促銷 + 點數，點數折抵上限為扣除促銷後的金額）
        OrderCheckoutService.PricingResult pricing = orderCheckoutService.calculate(
            command.brandId(), order.getItems(), order.getTotalAmount(),
            command.memberId(), command.pointsToUse());

        // 交易內同步扣點：不足時拋出 DomainException（422），整筆交易回滾
        orderCheckoutService.deductPoints(command.brandId(), command.memberId(), pricing.pointsUsed(),
            "訂單 " + order.getOrderNumber().value() + " 積點折抵");

        // 執行付款邏輯（Domain 會再次驗證狀態必須為 PENDING / HELD）
        order.processPayment(
            paymentMethod.getPaymentMethodId().longValue(),
            pricing.totalDiscount(),
            pricing.pointsUsed(),
            command.memberId(),
            memberSnapshot
        );

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
