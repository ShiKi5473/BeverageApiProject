package tw.niels.beverage_api_project.modules.order.application.handler;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.order.application.command.PosCheckoutCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

/**
 * POS 快速結帳 Handler（一步到位：建立 + 付款）
 *
 * 將訂單建立與付款合併為單一交易，適用於 POS 現場結帳場景。
 *
 * 處理流程：
 * 1. 驗證店家、付款方式；若有會員，驗證會員（存在且未停用，停用 → 422，m-5 新增）並建立 MemberSnapshot
 * 2. 以 OrderItemFactory 批次建立品項（真實快照、選項加價）
 * 3. 產生訂單號碼與預先指派的訂單 ID，呼叫 Order.place()（PENDING）
 * 4. 以 OrderCheckoutService 計算促銷折扣與點數折抵（點數折抵上限為扣除促銷後的金額）
 * 5. 於同一交易內同步扣點（不足 → DomainException 422，整筆回滾、不建立訂單）
 * 6. 呼叫 order.processPayment()（PENDING → PREPARING）
 * 7. 儲存並發佈領域事件
 *
 * 發佈事件：OrderPlacedEvent（建立）、OrderStatusChangedEvent（PENDING → PREPARING，KDS 依此顯示新訂單）
 *
 * 修改說明：
 * - 改用 OrderItemFactory、nextOrderNumber()、nextId()（P0-2、P0-3、P0-4）；
 * - 促銷折扣改為實際呼叫 PromotionService（原本 TODO 固定 0，B-3）；
 * - 點數改為交易內同步扣除（原本 AFTER_COMMIT 才扣、事前不驗證餘額，B-1）；
 * - 修正區域變數命名殘留（原本 {@code Order OrderEntity}）。
 */
@Component
public class PosCheckoutHandler {

    private final OrderRepository orderRepository;
    private final OrderItemFactory orderItemFactory;
    private final OrderNumberService orderNumberService;
    private final OrderCheckoutService orderCheckoutService;
    private final StoreService storeService;
    private final PaymentMethodService paymentMethodService;
    private final ApplicationEventPublisher eventPublisher;

    public PosCheckoutHandler(OrderRepository orderRepository,
                              OrderItemFactory orderItemFactory,
                              OrderNumberService orderNumberService,
                              OrderCheckoutService orderCheckoutService,
                              StoreService storeService,
                              PaymentMethodService paymentMethodService,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemFactory = orderItemFactory;
        this.orderNumberService = orderNumberService;
        this.orderCheckoutService = orderCheckoutService;
        this.storeService = storeService;
        this.paymentMethodService = paymentMethodService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 執行 POS 快速結帳
     *
     * @return 新訂單 ID
     */
    @Transactional
    public Long handle(PosCheckoutCommand command) {
        // 1. 驗證基礎資料（店家屬於本品牌、付款方式有效、會員存在）
        storeService.getStore(command.brandId(), command.storeId());
        PaymentMethodEntity paymentMethod = paymentMethodService.getByCode(command.paymentMethod());
        MemberSnapshot memberSnapshot = orderCheckoutService.resolveMemberSnapshot(command.brandId(), command.memberId());

        // 2. 批次建立品項
        List<OrderItem> domainItems = orderItemFactory.createItems(command.brandId(), command.items());

        // 3. 產生訂單號碼與 ID，建立訂單（初始狀態為 PENDING）
        OrderNumber orderNumber = OrderNumber.of(orderNumberService.nextOrderNumber(command.storeId()));
        Order order = Order.place(
            orderRepository.nextId(),
            command.brandId(),
            command.storeId(),
            command.userId(),
            orderNumber,
            domainItems,
            null
        );

        // 4. 計算折扣（促銷 + 點數；點數折抵上限為扣除促銷後的金額，超出的點數不扣）
        OrderCheckoutService.PricingResult pricing = orderCheckoutService.calculate(
            command.brandId(), order.getItems(), order.getTotalAmount(),
            command.memberId(), command.pointsToUse());

        // 5. 交易內同步扣點：不足時拋出 DomainException（422），整筆交易回滾
        orderCheckoutService.deductPoints(command.brandId(), command.memberId(), pricing.pointsUsed(),
            "訂單 " + orderNumber.value() + " 積點折抵");

        // 6. 執行付款流程（狀態轉為 PREPARING）
        order.processPayment(
            paymentMethod.getPaymentMethodId().longValue(),
            pricing.totalDiscount(),
            pricing.pointsUsed(),
            command.memberId(),
            memberSnapshot
        );

        // 7. 儲存並發布領域事件
        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return order.getId();
    }
}
