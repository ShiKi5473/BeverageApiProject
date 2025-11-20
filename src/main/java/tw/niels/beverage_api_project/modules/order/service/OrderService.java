package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.dto.PosCheckoutRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.order.state.OrderState;
import tw.niels.beverage_api_project.modules.order.state.OrderStateFactory;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import java.math.BigDecimal;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ControllerHelperService helperService;
    private final OrderNumberService orderNumberService;
    private final OrderStateFactory  orderStateFactory;
    private final OrderItemProcessorService orderItemProcessorService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MemberPointService memberPointService;

    public OrderService(OrderRepository orderRepository, StoreRepository storeRepository, UserRepository userRepository,
                        ControllerHelperService helperService,
                        OrderNumberService orderNumberService,
                        OrderStateFactory orderStateFactory,
                        OrderItemProcessorService orderItemProcessorService,
                        ApplicationEventPublisher eventPublisher,
                        PaymentMethodRepository paymentMethodRepository,
                        MemberPointService memberPointService) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.helperService = helperService;
        this.orderNumberService = orderNumberService;
        this.orderStateFactory = orderStateFactory;
        this.orderItemProcessorService = orderItemProcessorService;
        this.eventPublisher = eventPublisher;
        this.paymentMethodRepository = paymentMethodRepository;
        this.memberPointService = memberPointService;
    }


    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public Order createOrder(CreateOrderRequestDto requestDto) {
        User staff = getCurrentStaff();
        Long brandId = staff.getBrand().getBrandId();

        Long storeId = helperService.getCurrentStoreId();
        if (storeId == null) {
            // 品牌管理員或未分派的員工
            throw new BadRequestException("此帳號未綁定店家，無法建立訂單。");
        }

        Store store = storeRepository.findByBrand_BrandIdAndStoreId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家 (JWT storeId: " + storeId + ")"));

        // 建立 Order Entity
        Order order = new Order();
        order.setBrand(staff.getBrand());
        order.setStore(store);
        order.setStaff(staff);
        order.setMember(null);
        order.setOrderNumber(generateOrderNumber(store.getStoreId()));
        order.setStatus(requestDto.getStatus());
        order.setPaymentMethod(null); // 建立時一律為 null

        OrderItemProcessorService.ProcessedItemsResult result =
                orderItemProcessorService.processOrderItems(order, requestDto.getItems(), brandId);        order.setItems(result.orderItems);
        order.setTotalAmount(result.totalAmount);

        order.setPointsUsed(0L);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPointsEarned(0L);

        order.setFinalAmount(result.totalAmount);

        // 儲存訂單
        return orderRepository.save(order);
    }

    /**
     * 處理 POS 的「一步到位」結帳。
     * 這是一個單一交易，包含建立、計算、扣點、付款和發布事件。
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public Order completePosCheckout(PosCheckoutRequestDto requestDto) {
        User staff = getCurrentStaff();
        Long brandId = staff.getBrand().getBrandId();
        Long storeId = helperService.getCurrentStoreId();

        if (storeId == null) {
            throw new BadRequestException("此帳號未綁定店家，無法建立訂單。");
        }
        Store store = storeRepository.findByBrand_BrandIdAndStoreId(brandId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家 (JWT storeId: " + storeId + ")"));

        // 1. 建立 Order Entity
        Order order = new Order();
        order.setBrand(staff.getBrand());
        order.setStore(store);
        order.setStaff(staff);
        order.setOrderNumber(generateOrderNumber(store.getStoreId()));

        // 2. 處理品項 (複用現有邏輯)
        OrderItemProcessorService.ProcessedItemsResult result =
                orderItemProcessorService.processOrderItems(order, requestDto.getItems(), brandId);
        order.setItems(result.orderItems);
        order.setTotalAmount(result.totalAmount);

        // 3. 處理付款與會員 (複用 AbstractPrePaymentState 的邏輯)
        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));
        order.setPaymentMethod(paymentMethodEntity);

        User member = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long pointsToUse = 0L;

        if (requestDto.getMemberId() != null) {
            member = userRepository.findByBrand_BrandIdAndUserId(brandId, requestDto.getMemberId())
                    .filter(user -> user.getMemberProfile() != null)
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));
            order.setMember(member);

            if (requestDto.getPointsToUse() > 0) {
                pointsToUse = requestDto.getPointsToUse();
                discountAmount = memberPointService.calculateDiscountAmount(pointsToUse, order);
                memberPointService.usePoints(member, order, pointsToUse);
            }
        }
        order.setPointsUsed(pointsToUse);
        order.setDiscountAmount(discountAmount);
        BigDecimal finalAmount = order.getTotalAmount().subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }
        order.setFinalAmount(finalAmount);

        // 4. 【關鍵】直接將狀態設為 PREPARING
        order.setStatus(OrderStatus.PREPARING);
        order.setPointsEarned(0L); // 點數在 CLOSED 狀態才賺取

        // 5. 儲存
        Order savedOrder = orderRepository.save(order);

        // 6. 發布事件 (通知 KDS)
        eventPublisher.publishEvent(new OrderStateChangedEvent(savedOrder, null, OrderStatus.PREPARING));

        return savedOrder;
    }


    // 產生一個訂單號碼
    private String generateOrderNumber(Long storeId) {
        // 1. 獲取日期字串 (yyyyMMdd)
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());

        // 2. 獲取 Redis 流水號 (來自新服務)
        long sequence = orderNumberService.getNextStoreDailySequence(storeId);

        // 3. 格式化為新訂單號，例如：1-20251105-0001 (店家ID-日期-4位流水號)
        return String.format("%d-%s-%04d", storeId, date, sequence);
    }

    // 從 Security Context 取得當前登入的員工 User Entity
    private User getCurrentStaff() {
        Long currentUserId = helperService.getCurrentUserId();
        Long currentBrandId = helperService.getCurrentBrandId();

        return userRepository.findByBrand_BrandIdAndUserId(currentBrandId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到當前登入的員工資訊"));

    }


    /**
     * 計算訂單總金額，但不儲存訂單。
     */
    @Transactional(readOnly = true)
    public OrderTotalDto calculateOrderTotal(CreateOrderRequestDto requestDto) {
        User staff = getCurrentStaff();
        Long brandId = staff.getBrand().getBrandId();

        OrderItemProcessorService.ProcessedItemsResult result =
                orderItemProcessorService.processOrderItems(null, requestDto.getItems(), brandId);

        return new OrderTotalDto(result.totalAmount);
    }

    /**
     * 查詢訂單列表
     * 
     * @param brandId 品牌 ID (來自 JWT 或上下文)
     * @param storeId 要查詢的店家 ID
     * @param status  可選的訂單狀態篩選條件
     * @return 訂單列表
     */
    @Transactional(readOnly = true)
    public List<Order> getOrders(Long brandId, Long storeId, Optional<OrderStatus> status) {
        // 在這裡可以加入權限檢查，例如檢查目前登入的使用者是否有權限查看 storeId 的訂單

        if (status.isPresent()) {
            return orderRepository.findAllByBrand_BrandIdAndStore_StoreIdAndStatus(brandId, storeId, status.get());
        } else {
            return orderRepository.findAllByBrand_BrandIdAndStore_StoreId(brandId, storeId);
        }
    }

    /**
     * 查詢單一訂單詳情
     * 
     * @param brandId 品牌 ID (來自 JWT 或上下文)
     * @param orderId 要查詢的訂單 ID
     * @return 訂單實體
     * @throws ResourceNotFoundException 如果訂單不存在或不屬於該品牌
     */
    @Transactional(readOnly = true)
    public Order getOrderDetails(Long brandId, Long orderId) {
        Order order = orderRepository.findByBrand_BrandIdAndOrderId(brandId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到訂單，ID：" + orderId));
        // TODO 在這裡可以加入權限檢查，例如檢查目前使用者是否能查看此訂單 (可能基於店家 ID)
        return order;
    }

    /**
     * 更新訂單狀態
     * 
     * @param brandId   品牌 ID (來自 JWT 或上下文)
     * @param orderId   要更新的訂單 ID
     * @param newStatus 新的訂單狀態
     * @return 更新後的訂單實體
     * @throws ResourceNotFoundException 如果訂單不存在或不屬於該品牌
     * @throws BadRequestException       如果狀態轉換無效 (例如從 COMPLETED 更新)
     */
    @Transactional
    public Order updateOrderStatus(Long brandId, Long orderId, OrderStatus newStatus) {
        Order order = getOrderDetails(brandId, orderId);

        // 1. 根據訂單「當前」狀態，取得對應的狀態物件
        OrderState currentState = orderStateFactory.getState(order.getStatus());

        // 2. 委派「動作」給狀態物件
        if (newStatus == OrderStatus.READY_FOR_PICKUP) {
            // 由 KDS 觸發 (PREPARING -> READY_FOR_PICKUP)
            // currentState 應該是 PreparingState
            // 呼叫 PreparingState.complete() 會將狀態轉為 READY_FOR_PICKUP
            currentState.complete(order);
        } else if (newStatus == OrderStatus.CLOSED) {
            // 由 POS 觸發 (READY_FOR_PICKUP -> CLOSED)
            // currentState 應該是 ReadyForPickupState
            // 呼叫 ReadyForPickupState.complete() 會將狀態轉為 CLOSED
            currentState.complete(order);

        } else if (newStatus == OrderStatus.CANCELLED) {
            currentState.cancel(order);
        } else {
            // 拋出一個更通用的錯誤訊息
            throw new BadRequestException("此 API 不支援將狀態更新為 " + newStatus);
        }
        // 狀態物件內部已經修改了 order 的狀態，我們只需儲存
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateHeldOrder(Long brandId, Long orderId, CreateOrderRequestDto dto) {
        // 1. 取得訂單 (並驗證 brandId)
        Order order = getOrderDetails(brandId, orderId);

        // 2. 取得目前狀態 (例如 HELD)
        OrderState currentState = orderStateFactory.getState(order.getStatus());

        // 3. 委派 "update" 動作
        // (如果狀態不是 HELD/PENDING，currentState.update() 會自動拋出例外)
        currentState.update(order, dto);

        return orderRepository.save(order);
    }

    @Transactional
    public Order acceptOrder(Long brandId, Long orderId) {
        // 1. 取得訂單
        Order order = getOrderDetails(brandId, orderId);

        // 2. 根據訂單「當前」狀態，取得對應的狀態物件
        // 這裡 currentState 應該是 AwaitingAcceptanceState
        OrderState currentState = orderStateFactory.getState(order.getStatus());

        // 3. 委派「接單」動作
        // (如果狀態不是 AWAITING_ACCEPTANCE，會自動拋出例外)
        currentState.accept(order);

        // 4. 儲存
        return orderRepository.save(order);
    }

    /**
     * 處理付款。
     * 將「付款」動作委派給目前的狀態物件 (PendingState 或 HeldState)。
     */
    @Transactional
    public Order processPayment(Long brandId, Long orderId, ProcessPaymentRequestDto requestDto) {
        // 1. 取得訂單
        Order order = getOrderDetails(brandId, orderId);

        // 2. 根據訂單「當前」狀態，取得對應的狀態物件
        OrderState currentState = orderStateFactory.getState(order.getStatus());

        // 3. 委派「付款」動作給狀態物件
        // (如果狀態不是 PENDING/HELD，currentState.processPayment() 會自動拋出例外)
        currentState.processPayment(order, requestDto);

        // 狀態物件內部已經修改了 order 的狀態，我們只需儲存
        return orderRepository.save(order);
    }

}
