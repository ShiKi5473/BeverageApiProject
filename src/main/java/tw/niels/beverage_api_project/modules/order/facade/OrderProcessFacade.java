package tw.niels.beverage_api_project.modules.order.facade;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.dto.PosCheckoutRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.order.service.OrderService;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.service.StoreService;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;

/**
 * 訂單處理外觀模式 (Facade)
 * 負責協調 OrderEntity, Member, Inventory, PromotionEntity 等服務。
 */
@Service
public class OrderProcessFacade {

    private final OrderService orderService;
    private final MemberPointService memberPointService;
    private final PromotionService promotionService;
    private final OrderItemProcessorService orderItemProcessorService;
    // 跨模組查詢透過各模組的 Service，不直接依賴其 Repository
    private final StoreService storeService;
    private final UserService userService;
    private final PaymentMethodService paymentMethodService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderProcessFacade(OrderService orderService,
                              MemberPointService memberPointService,
                              PromotionService promotionService,
                              OrderItemProcessorService orderItemProcessorService,
                              StoreService storeService,
                              UserService userService,
                              PaymentMethodService paymentMethodService,
                              ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.memberPointService = memberPointService;
        this.promotionService = promotionService;
        this.orderItemProcessorService = orderItemProcessorService;
        this.storeService = storeService;
        this.userService = userService;
        this.paymentMethodService = paymentMethodService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 處理 POS 的「一步到位」結帳。
     * 包含：建立訂單 -> 計算金額 -> 扣點數 -> 促銷計算 -> 存檔 -> 發送 KDS 事件
     */
    @Transactional
    public OrderEntity processPosCheckout(Long brandId, Long storeId, Long staffUserId, PosCheckoutRequestDto requestDto) {
        // 1. 驗證基礎資料（透過各模組 Service，不直接存取跨模組 Repository）
        StoreEntity StoreEntity = storeService.getStore(brandId, storeId);
        UserEntity staff = userService.getUser(brandId, staffUserId);

        PaymentMethodEntity paymentMethod = paymentMethodService.getByCode(requestDto.getPaymentMethod());

        // 2. 初始化訂單 (委派給 OrderService 建立空殼)
        OrderEntity OrderEntity = orderService.initOrder(staff, StoreEntity);

        // 3. 處理訂單品項與金額 (委派給 Processor)
        var itemResult = orderItemProcessorService.processOrderItems(OrderEntity, requestDto.getItems(), brandId);
        OrderEntity.setItems(itemResult.orderItems());
        OrderEntity.setTotalAmount(itemResult.totalAmount());

        // 在這裡先存檔一次！讓 OrderEntity 變成 Persistent 狀態 (有 ID)
        OrderEntity = orderService.saveOrder(OrderEntity);

        // 4. 庫存管理模式是 「週期性盤點 (Periodic Review)」，不進行實時扣減。

        // 5. 處理會員與點數
        BigDecimal pointDiscount = BigDecimal.ZERO;

        if (requestDto.getMemberId() != null) {
            UserEntity member = userService.getMember(brandId, requestDto.getMemberId());

            OrderEntity.setMember(member);
            // 建立會員快照
            orderService.snapshotMember(OrderEntity, member);

            if (requestDto.getPointsToUse() > 0) {
                pointDiscount = memberPointService.calculateDiscountAmount(requestDto.getPointsToUse(), OrderEntity);
                memberPointService.usePoints(member, OrderEntity, requestDto.getPointsToUse());
                OrderEntity.setPointsUsed(requestDto.getPointsToUse());
            }
        }

        // 6. 計算最佳促銷折扣 (委派給 PromotionService)
        BigDecimal promoDiscount = promotionService.calculateBestDiscount(OrderEntity);

        // 7. 結算最終金額
        BigDecimal totalDiscount = pointDiscount.add(promoDiscount);
        OrderEntity.setDiscountAmount(totalDiscount);

        BigDecimal finalAmount = OrderEntity.getTotalAmount().subtract(totalDiscount);
        OrderEntity.setFinalAmount(finalAmount.max(BigDecimal.ZERO));
        OrderEntity.setPaymentMethod(paymentMethod);

        // 8. 設定狀態並存檔
        OrderEntity.setStatus(OrderStatus.PREPARING); // POS 直接結帳視為進入製作中
        OrderEntity.setPointsEarned(0L); // 點數在 CLOSED 狀態才賺取

        OrderEntity savedOrder = orderService.saveOrder(OrderEntity);

        // 9. 發布領域事件 (通知 KDS)
        eventPublisher.publishEvent(new OrderStateChangedEvent(savedOrder, null, OrderStatus.PREPARING));

        return savedOrder;
    }

    /**
     * 建立一般訂單 (例如暫存單或線上單)
     */
    @Transactional
    public OrderEntity createOrder(Long brandId, Long storeId, Long staffUserId, CreateOrderRequestDto requestDto) {
        StoreEntity StoreEntity = storeService.getStore(brandId, storeId);
        UserEntity staff = userService.getUser(brandId, staffUserId);

        OrderEntity OrderEntity = orderService.initOrder(staff, StoreEntity);
        OrderEntity.setStatus(requestDto.getStatus());

        var result = orderItemProcessorService.processOrderItems(OrderEntity, requestDto.getItems(), brandId);
        OrderEntity.setItems(result.orderItems());
        OrderEntity.setTotalAmount(result.totalAmount());
        OrderEntity.setFinalAmount(result.totalAmount()); // 暫無折扣

        return orderService.saveOrder(OrderEntity);
    }

    /**
     * 試算訂單金額 (不存檔)
     * 從 OrderService 遷移過來的邏輯
     */
    @Transactional(readOnly = true)
    public OrderTotalDto calculateOrderTotal(Long brandId, CreateOrderRequestDto requestDto) {
        // 傳入 null 作為 OrderEntity，因為只是試算，不需要實體
        var result = orderItemProcessorService.processOrderItems(null, requestDto.getItems(), brandId);
        return new OrderTotalDto(result.totalAmount());
    }
}