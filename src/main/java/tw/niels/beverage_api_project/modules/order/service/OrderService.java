package tw.niels.beverage_api_project.modules.order.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
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
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    // 用於生成簡單的訂單流水號，之後改成用redis取得流水號
    private static final AtomicLong orderCounter = new AtomicLong(0);

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
            PaymentMethodRepository paymentMethodRepository) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequestDto requestDto) {
        User staff = getCurrentStaff();
        Long brandId = staff.getBrand().getBrandId();

        // 驗證店家是否存在於該品牌底下
        Store store = storeRepository.findByBrand_BrandIdAndStoreId(brandId, requestDto.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("找不到店家，ID：" + requestDto.getStoreId()));

        // 查找會員 (如果有的話)
        User member = null;
        if (requestDto.getMemberId() != null) {
            member = userRepository.findById(requestDto.getMemberId())
                    .filter(user -> user.getMemberProfile() != null && user.getBrand().getBrandId().equals(brandId))
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));
        }

        // 處理支付方式
        PaymentMethodEntity paymentMethodEntity = null;
        if (requestDto.getPaymentMethod() != null && !requestDto.getPaymentMethod().isBlank()) {
            paymentMethodEntity = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                    .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));
            // 您可以在這裡添加更多邏輯，例如檢查該支付方式是否可用 is_active
        }

        // 建立 Order Entity
        Order order = new Order();
        order.setBrand(staff.getBrand());
        order.setStore(store);
        order.setMember(member);
        order.setStaff(staff);
        order.setOrderNumber(generateOrderNumber(store.getStoreId()));
        order.setStatus(OrderStatus.PENDING);

        ProcessedItemsResult result = processOrderItems(order, requestDto.getItems(), brandId);
        order.setItems(result.orderItems);
        order.setTotalAmount(result.totalAmount);
        order.setFinalAmount(result.totalAmount); // 暫不處理折扣

        return orderRepository.save(order);
    }

    // 產生一個簡易的訂單號碼
    private String generateOrderNumber(Long storeId) {
        String date = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        long count = orderCounter.incrementAndGet() % 1000; // 取 3 位數，避免過長
        return String.format("%d-%s-%03d", storeId, date, count);
    }

    // 從 Security Context 取得當前登入的員工 User Entity
    private User getCurrentStaff() {
        AppUserDetails userDetails = (AppUserDetails) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        return userRepository.findById(userDetails.getUserId())
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
        // 在這裡可以加入權限檢查，例如檢查目前使用者是否能查看此訂單 (可能基於店家 ID)
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

        // 簡單的狀態更新
        order.setStatus(newStatus);

        // 如果狀態更新為已完成，記錄完成時間
        if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedTime(new Date());
            // TODO: 在此處或透過事件監聽器觸發點數累積、庫存扣除等後續邏輯
            // 例如： memberPointService.addPoints(order.getMember().getUserId(),
            // calculatePoints(order.getFinalAmount()));
            // 例如： inventoryService.deductStock(order.getItems());
        }

        return orderRepository.save(order);
    }

}
