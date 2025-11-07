package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.state.OrderState;
import tw.niels.beverage_api_project.modules.order.state.OrderStateFactory;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

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

    public OrderService(OrderRepository orderRepository, StoreRepository storeRepository, UserRepository userRepository,
                        ControllerHelperService helperService,
                        OrderNumberService orderNumberService,
                        OrderStateFactory orderStateFactory,
                        OrderItemProcessorService orderItemProcessorService) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.helperService = helperService;
        this.orderNumberService = orderNumberService;
        this.orderStateFactory = orderStateFactory;
        this.orderItemProcessorService = orderItemProcessorService;
    }

    // 用於封裝品項處理結果的內部類別
    private static class ProcessedItemsResult {
        final Set<OrderItem> orderItems;
        final BigDecimal totalAmount;

        ProcessedItemsResult(Set<OrderItem> orderItems, BigDecimal totalAmount) {
            this.orderItems = orderItems;
            this.totalAmount = totalAmount;
        }
    }





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
        return userRepository.findById(currentUserId)
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
        if (newStatus == OrderStatus.CLOSED) {
            currentState.complete(order);
        } else if (newStatus == OrderStatus.CANCELLED) {
            currentState.cancel(order);
        } else {
            // (可選) 暫不支援 KDS 直接更新到其他狀態 (例如 PREPARING)
            throw new BadRequestException("此 API 僅支援將狀態更新為 CLOSED 或 CANCELLED。");
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

    /**
     * 【重構】
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
