package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
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
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MemberPointService memberPointService;
    private final ControllerHelperService helperService;
    private final OrderNumberService orderNumberService;



    // 用於封裝品項處理結果的內部類別
    private static class ProcessedItemsResult {
        final Set<OrderItem> orderItems;
        final BigDecimal totalAmount;

        ProcessedItemsResult(Set<OrderItem> orderItems, BigDecimal totalAmount) {
            this.orderItems = orderItems;
            this.totalAmount = totalAmount;
        }
    }

    public OrderService(OrderRepository orderRepository, StoreRepository storeRepository, UserRepository userRepository,
            ProductRepository productRepository, ProductOptionRepository productOptionRepository,
            PaymentMethodRepository paymentMethodRepository,
            MemberPointService memberPointService,
            ControllerHelperService helperService,
                        OrderNumberService orderNumberService) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.memberPointService = memberPointService;
        this.helperService = helperService;
        this.orderNumberService = orderNumberService;
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

        ProcessedItemsResult result = processOrderItems(order, requestDto.getItems(), brandId);
        order.setItems(result.orderItems);
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
     * 處理訂單品項的核心邏輯：建立 OrderItem 實體並計算總金額
     */
    private ProcessedItemsResult processOrderItems(Order order, List<OrderItemDto> itemDtos, Long brandId) {
        Set<OrderItem> orderItems = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemDto : itemDtos) {
            Product product = productRepository.findByBrand_BrandIdAndProductId(brandId, itemDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + itemDto.getProductId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemDto.getQuantity());
            orderItem.setNotes(itemDto.getNotes());

            BigDecimal optionsPrice = BigDecimal.ZERO;
            if (itemDto.getOptionIds() != null && !itemDto.getOptionIds().isEmpty()) {
                Set<ProductOption> options = productOptionRepository.findByOptionIdIn(itemDto.getOptionIds());
                if (options.size() != itemDto.getOptionIds().size()) {
                    throw new BadRequestException("部分選項 ID 無效");
                }
                orderItem.setOptions(options);
                optionsPrice = options.stream()
                        .map(ProductOption::getPriceAdjustment)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

            }

            BigDecimal unitPrice = product.getBasePrice().add(optionsPrice);
            orderItem.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(itemDto.getQuantity()));
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }
        return new ProcessedItemsResult(orderItems, totalAmount);
    }

    /**
     * 計算訂單總金額，但不儲存訂單。
     */
    @Transactional(readOnly = true)

    public OrderTotalDto calculateOrderTotal(CreateOrderRequestDto requestDto) {
        User staff = getCurrentStaff();
        Long brandId = staff.getBrand().getBrandId();

        ProcessedItemsResult result = processOrderItems(null, requestDto.getItems(), brandId);

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
        Order order = orderRepository.findByBrand_BrandIdAndOrderId(brandId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到訂單，ID：" + orderId));

        // 可以在此加入更複雜的狀態轉換邏輯檢查
        // 例如：只有 PENDING 或 PREPARING 狀態才能變成 CANCELLED
        // 例如：只有 PREPARING 狀態才能變成 COMPLETED
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("訂單狀態 " + order.getStatus() + " 無法被更新。");
        }

//        OrderStatus oldStatus = order.getStatus(); // 記錄舊狀態
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedTime(new Date());

            // --- 【新增】 點數處理：計算並增加賺取的點數 ---
            if (order.getMember() != null) {
                Long pointsEarned = memberPointService.calculatePointsEarned(order.getFinalAmount());
                order.setPointsEarned(pointsEarned); // 更新訂單上的記錄
                if (pointsEarned > 0) {
                    // 這個方法內部有 @Transactional(propagation = Propagation.MANDATORY)
                    memberPointService.earnPoints(order.getMember(), order, pointsEarned);
                }
            }
            // --- 點數處理結束 ---

            // TODO: 在此處或透過事件監聽器觸發庫存扣除等後續邏輯
        }
        // 可選：處理取消訂單時，是否需要回補點數？
        // else if (newStatus == OrderStatus.CANCELLED && oldStatus !=
        // OrderStatus.CANCELLED) {
        // if (order.getMember() != null && order.getPointsUsed() > 0) {
        // // 需要一個回補點數的方法
        // // memberPointService.refundPoints(order.getMember(), order,
        // order.getPointsUsed());
        // }
        // }
        return orderRepository.save(order);
    }

    @Transactional
    public Order processPayment(Long brandId, Long orderId, ProcessPaymentRequestDto requestDto) {
        // 1.
        Order order = orderRepository.findByBrand_BrandIdAndOrderId(brandId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到訂單，ID：" + orderId));

        // 2.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.HELD) {
            throw new BadRequestException("訂單狀態為 " + order.getStatus() + "，無法進行結帳。");
        }

        // 3.
        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));

        order.setPaymentMethod(paymentMethodEntity);

        // 4.
        User member = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long pointsToUse = 0L;

        if (requestDto.getMemberId() != null) {
            member = userRepository.findById(requestDto.getMemberId())
                    .filter(user -> user.getMemberProfile() != null && user.getBrand().getBrandId().equals(brandId))
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));

            order.setMember(member); //

            if (requestDto.getPointsToUse() > 0) {
                pointsToUse = requestDto.getPointsToUse();
                //
                discountAmount = memberPointService.calculateDiscountAmount(pointsToUse);
                //
                memberPointService.usePoints(member, order, pointsToUse);
            }
        }

        // 5.
        order.setPointsUsed(pointsToUse);
        order.setDiscountAmount(discountAmount);

        BigDecimal finalAmount = order.getTotalAmount().subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }
        order.setFinalAmount(finalAmount);

        // 6.
        //
        order.setStatus(OrderStatus.PREPARING); //

        //
        if (order.getStatus() == OrderStatus.PREPARING) {
            // TODO新增狀態機
        }

        return orderRepository.save(order);
    }

}
