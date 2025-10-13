package tw.niels.beverage_api_project.modules.order.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
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
            ProductRepository productRepository, ProductOptionRepository productOptionRepository) {
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
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

}
