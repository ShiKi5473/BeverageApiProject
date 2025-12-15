package tw.niels.beverage_api_project.modules.order.facade;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.dto.PosCheckoutRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.order.service.OrderService;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;

/**
 * 訂單處理外觀模式 (Facade)
 * 負責協調 Order, Member, Inventory, Promotion 等服務。
 */
@Service
public class OrderProcessFacade {

    private final OrderService orderService;
    private final MemberPointService memberPointService;
    private final PromotionService promotionService;
    private final OrderItemProcessorService orderItemProcessorService;
    private final InventoryService inventoryService; // 預留給庫存扣減

    // Repositories 用於查驗基礎資料
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderProcessFacade(OrderService orderService,
                              MemberPointService memberPointService,
                              PromotionService promotionService,
                              OrderItemProcessorService orderItemProcessorService,
                              InventoryService inventoryService,
                              StoreRepository storeRepository,
                              UserRepository userRepository,
                              PaymentMethodRepository paymentMethodRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.memberPointService = memberPointService;
        this.promotionService = promotionService;
        this.orderItemProcessorService = orderItemProcessorService;
        this.inventoryService = inventoryService;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 處理 POS 的「一步到位」結帳。
     * 包含：建立訂單 -> 計算金額 -> 扣點數 -> 促銷計算 -> 存檔 -> 發送 KDS 事件
     */
    @Transactional
    public Order processPosCheckout(Long brandId, Long storeId, Long staffUserId, PosCheckoutRequestDto requestDto) {
        // 1. 驗證基礎資料
        Store store = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家 (ID: " + storeId + ")"));

        User staff = userRepository.findByBrand_IdAndId(brandId, staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到員工 (ID: " + staffUserId + ")"));

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));

        // 2. 初始化訂單 (委派給 OrderService 建立空殼)
        Order order = orderService.initOrder(staff, store);

        // 3. 處理訂單品項與金額 (委派給 Processor)
        var itemResult = orderItemProcessorService.processOrderItems(order, requestDto.getItems(), brandId);
        order.setItems(itemResult.orderItems);
        order.setTotalAmount(itemResult.totalAmount);

        // 在這裡先存檔一次！讓 Order 變成 Persistent 狀態 (有 ID)
        order = orderService.saveOrder(order);

        // 4. (TODO) 處理庫存扣減 (未來串接 InventoryService)
        // inventoryService.deductInventory(...);

        // 5. 處理會員與點數
        BigDecimal pointDiscount = BigDecimal.ZERO;

        if (requestDto.getMemberId() != null) {
            User member = userRepository.findByBrand_IdAndId(brandId, requestDto.getMemberId())
                    .filter(u -> u.getMemberProfile() != null)
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));

            order.setMember(member);
            // 建立會員快照
            orderService.snapshotMember(order, member);

            if (requestDto.getPointsToUse() > 0) {
                pointDiscount = memberPointService.calculateDiscountAmount(requestDto.getPointsToUse(), order);
                memberPointService.usePoints(member, order, requestDto.getPointsToUse());
                order.setPointsUsed(requestDto.getPointsToUse());
            }
        }

        // 6. 計算最佳促銷折扣 (委派給 PromotionService)
        BigDecimal promoDiscount = promotionService.calculateBestDiscount(order);

        // 7. 結算最終金額
        BigDecimal totalDiscount = pointDiscount.add(promoDiscount);
        order.setDiscountAmount(totalDiscount);

        BigDecimal finalAmount = order.getTotalAmount().subtract(totalDiscount);
        order.setFinalAmount(finalAmount.max(BigDecimal.ZERO));
        order.setPaymentMethod(paymentMethod);

        // 8. 設定狀態並存檔
        order.setStatus(OrderStatus.PREPARING); // POS 直接結帳視為進入製作中
        order.setPointsEarned(0L); // 點數在 CLOSED 狀態才賺取

        Order savedOrder = orderService.saveOrder(order);

        // 9. 發布領域事件 (通知 KDS)
        eventPublisher.publishEvent(new OrderStateChangedEvent(savedOrder, null, OrderStatus.PREPARING));

        return savedOrder;
    }

    /**
     * 建立一般訂單 (例如暫存單或線上單)
     */
    @Transactional
    public Order createOrder(Long brandId, Long storeId, Long staffUserId, CreateOrderRequestDto requestDto) {
        Store store = storeRepository.findByBrand_IdAndId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家"));
        User staff = userRepository.findByBrand_IdAndId(brandId, staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到員工"));

        Order order = orderService.initOrder(staff, store);
        order.setStatus(requestDto.getStatus());

        var result = orderItemProcessorService.processOrderItems(order, requestDto.getItems(), brandId);
        order.setItems(result.orderItems);
        order.setTotalAmount(result.totalAmount);
        order.setFinalAmount(result.totalAmount); // 暫無折扣

        return orderService.saveOrder(order);
    }

    /**
     * 試算訂單金額 (不存檔)
     * 從 OrderService 遷移過來的邏輯
     */
    @Transactional(readOnly = true)
    public OrderTotalDto calculateOrderTotal(Long brandId, CreateOrderRequestDto requestDto) {
        // 傳入 null 作為 order，因為只是試算，不需要實體
        var result = orderItemProcessorService.processOrderItems(null, requestDto.getItems(), brandId);
        return new OrderTotalDto(result.totalAmount);
    }
}