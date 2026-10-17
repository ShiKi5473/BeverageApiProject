package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

import java.util.List;
import java.util.Optional;

/**
 * 建立線上訂單 Handler（新增，B-4）
 *
 * 由 RabbitMQ 消費者（OrderMessageConsumer）呼叫，取代已刪除的 OrderProcessFacade。
 *
 * 處理流程：
 * 1. 冪等檢查：以 requestId（ticketId）查詢，已建立過就直接回傳既有訂單（不重複建單、不重發事件）
 * 2. 驗證分店屬於本品牌；會員下單時驗證會員並建立 MemberSnapshot
 * 3. 以 OrderItemFactory 批次建立品項（真實快照、選項加價、Jsoup 清洗備註）
 * 3.5 新增（item 5）：以 OrderCheckoutService.calculate() 計算促銷折扣（與 POS 結帳、/orders/preview 共用同一套規則），
 *    線上訂單不支援點數折抵（pointsToUse 固定為 0）
 * 4. 產生訂單號碼與預先指派的訂單 ID，呼叫 Order.placeOnline()（初始狀態固定 AWAITING_ACCEPTANCE，
 *    綁定 memberId 或 guestRef、requestId = ticketId，並寫入促銷折扣與應付金額）
 * 5. 儲存並發佈領域事件（只發佈 domain event，不再發佈舊的 OrderStateChangedEvent）
 *
 * 發佈事件：OrderPlacedEvent（KDS 依載入後的 AWAITING_ACCEPTANCE 狀態推播「待接單」）
 *
 * 併發保護：orders.request_id 有部分唯一索引（V18），同一 ticket 同時被處理時，
 * 後提交者會因唯一鍵衝突回滾，消費者再以 {@link #findExisting} 確認訂單已建立。
 */
@Component
public class PlaceOnlineOrderHandler {

    private final OrderRepository orderRepository;
    private final OrderItemFactory orderItemFactory;
    private final OrderNumberService orderNumberService;
    private final OrderCheckoutService orderCheckoutService;
    private final StoreService storeService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceOnlineOrderHandler(OrderRepository orderRepository,
                                   OrderItemFactory orderItemFactory,
                                   OrderNumberService orderNumberService,
                                   OrderCheckoutService orderCheckoutService,
                                   StoreService storeService,
                                   ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemFactory = orderItemFactory;
        this.orderNumberService = orderNumberService;
        this.orderCheckoutService = orderCheckoutService;
        this.storeService = storeService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 線上訂單建立結果。
     *
     * @param orderId     訂單 ID
     * @param orderNumber 訂單號碼
     * @param status      訂單狀態（Domain 狀態名稱）
     * @param created     本次是否新建（false 代表冪等命中既有訂單）
     */
    public record Result(Long orderId, String orderNumber, String status, boolean created) {

        static Result of(Order order, boolean created) {
            return new Result(order.getId(), order.getOrderNumber().value(), order.getStatus().name(), created);
        }
    }

    /**
     * 建立線上訂單（冪等）。
     *
     * @param command 建立線上訂單命令
     * @return 建立結果（重送時回傳既有訂單）
     */
    @Transactional
    public Result handle(PlaceOnlineOrderCommand command) {
        if (command.requestId() == null || command.requestId().isBlank()) {
            throw new BadRequestException("線上訂單缺少請求 ID（ticketId）");
        }
        if (command.brandId() == null || command.storeId() == null) {
            throw new BadRequestException("線上訂單缺少品牌或分店資訊");
        }

        // 1. 冪等檢查：同一 ticket 已建立過訂單就直接回傳（MQ 重送不會重複建單）
        Optional<Order> existing = orderRepository.findByRequestId(command.brandId(), command.requestId());
        if (existing.isPresent()) {
            return Result.of(existing.get(), false);
        }

        // 2. 驗證分店屬於本品牌；會員下單時建立會員快照（同時驗證會員存在）
        storeService.getStore(command.brandId(), command.storeId());
        MemberSnapshot memberSnapshot = orderCheckoutService.resolveMemberSnapshot(command.brandId(), command.memberId());

        // 3. 批次建立品項
        List<OrderItem> domainItems = orderItemFactory.createItems(command.brandId(), command.items());

        // 3.5 新增（item 5）：促銷折扣與 POS 結帳 / 金額試算使用同一個 OrderCheckoutService.calculate()，
        //     確保同樣的品項在線上下單時的 totalAmount / discountAmount / finalAmount 與 preview 回傳的一致。
        //     線上訂單不開放點數折抵：pointsToUse 固定 0（calculate 只在點數 > 0 時才使用 memberId）。
        Money totalAmount = domainItems.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);
        OrderCheckoutService.PricingResult pricing = orderCheckoutService.calculate(
            command.brandId(), domainItems, totalAmount, command.memberId(), 0L);

        // 4. 產生訂單號碼與 ID，建立線上訂單（初始狀態固定 AWAITING_ACCEPTANCE，由店家接單後才製作）
        OrderNumber orderNumber = OrderNumber.of(orderNumberService.nextOrderNumber(command.storeId()));
        Order order = Order.placeOnline(
            orderRepository.nextId(),
            command.brandId(),
            command.storeId(),
            command.memberId(),
            memberSnapshot,
            // 會員下單時以會員 ID 推播通知，不需要 guestRef
            command.memberId() == null ? command.guestRef() : null,
            command.requestId(),
            orderNumber,
            domainItems,
            OrderItemFactory.sanitize(command.customerNote()),
            // 新增：建立時即套用促銷折扣（線上訂單不經過 processPayment）
            pricing.promotionDiscount()
        );

        // 5. 儲存並發佈領域事件（AFTER_COMMIT 監聽者在交易提交後才收到）
        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return Result.of(order, true);
    }

    /**
     * 查詢某個 ticket 是否已建立訂單（消費者處理失敗時用來判斷是否其實已成功，例如唯一鍵衝突）。
     *
     * @param brandId   品牌 ID
     * @param requestId ticketId
     * @return 既有訂單的結果
     */
    @Transactional(readOnly = true)
    public Optional<Result> findExisting(Long brandId, String requestId) {
        if (brandId == null || requestId == null) {
            return Optional.empty();
        }
        return orderRepository.findByRequestId(brandId, requestId)
            .map(order -> Result.of(order, false));
    }
}
