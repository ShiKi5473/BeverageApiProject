// 路徑：src/main/java/tw/niels/beverage_api_project/modules/order/service/OrderService.java
package tw.niels.beverage_api_project.modules.order.service;

// 保留給 processPayment 等狀態機用

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.state.OrderState;
import tw.niels.beverage_api_project.modules.order.state.OrderStateFactory;
import tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.user.entity.User;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderNumberService orderNumberService;
    private final OrderStateFactory orderStateFactory;

    public OrderService(OrderRepository orderRepository,
                        OrderNumberService orderNumberService,
                        OrderStateFactory orderStateFactory) {
        this.orderRepository = orderRepository;
        this.orderNumberService = orderNumberService;
        this.orderStateFactory = orderStateFactory;
    }

    /**
     * 【重構後】初始化一個空的訂單實體 (包含流水號生成)
     */
    public Order initOrder(User staff, Store store) {
        Order order = new Order();
        order.setBrand(staff.getBrand());
        order.setStore(store);
        order.setStaff(staff);
        order.setOrderNumber(generateOrderNumber(store.getStoreId()));

        // 初始化預設值
        order.setPointsUsed(0L);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPointsEarned(0L);

        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO); // 防止存檔時為 null
        order.setFinalAmount(BigDecimal.ZERO); // 防止存檔時為 null (這行是解決本次錯誤的關鍵)

        // 設定初始狀態，避免第一次 save 時報錯
        order.setStatus(OrderStatus.PENDING);

        return order;
    }

    /**
     * 【重構後】單純的儲存操作
     */
    @Transactional
    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    /**
     * 【重構後】建立會員快照
     */
    public void snapshotMember(Order order, User member) {
        if (member != null && member.getMemberProfile() != null) {
            MemberSnapshot snapshot = new MemberSnapshot(
                    member.getUserId(),
                    member.getMemberProfile().getFullName(),
                    member.getPrimaryPhone(),
                    member.getMemberProfile().getEmail()
            );
            order.setMemberSnapshot(snapshot);
        }
    }

    // --- 以下保留查詢與狀態機邏輯 (因狀態機邏輯較複雜，暫不移動至 Facade) ---

    // 產生一個訂單號碼 (Private helper)
    private String generateOrderNumber(Long storeId) {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        long sequence = orderNumberService.getNextStoreDailySequence(storeId);
        return String.format("%d-%s-%04d", storeId, date, sequence);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(Long brandId, Long storeId, Optional<OrderStatus> status) {
        if (status.isPresent()) {
            return orderRepository.findAllByBrand_IdAndStore_IdAndStatus(brandId, storeId, status.get());
        } else {
            return orderRepository.findAllByBrand_IdAndStore_Id(brandId, storeId);
        }
    }

    @Transactional(readOnly = true)
    public Order getOrderDetails(Long brandId, Long orderId) {
        return orderRepository.findByBrand_IdAndId(brandId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到訂單，ID：" + orderId));
    }

    @Transactional
    public Order updateOrderStatus(Long brandId, Long orderId, OrderStatus newStatus) {
        Order order = getOrderDetails(brandId, orderId);
        OrderState currentState = orderStateFactory.getState(order.getStatus());

        if (newStatus == OrderStatus.READY_FOR_PICKUP) {
            currentState.complete(order);
        } else if (newStatus == OrderStatus.CLOSED) {
            currentState.complete(order);
        } else if (newStatus == OrderStatus.CANCELLED) {
            currentState.cancel(order);
        } else {
            throw new BadRequestException("此 API 不支援將狀態更新為 " + newStatus);
        }
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateHeldOrder(Long brandId, Long orderId, CreateOrderRequestDto dto) {
        Order order = getOrderDetails(brandId, orderId);
        OrderState currentState = orderStateFactory.getState(order.getStatus());
        currentState.update(order, dto);
        return orderRepository.save(order);
    }

    @Transactional
    public Order acceptOrder(Long brandId, Long orderId) {
        Order order = getOrderDetails(brandId, orderId);
        OrderState currentState = orderStateFactory.getState(order.getStatus());
        currentState.accept(order);
        return orderRepository.save(order);
    }

    @Transactional
    public Order processPayment(Long brandId, Long orderId, ProcessPaymentRequestDto requestDto) {
        Order order = getOrderDetails(brandId, orderId);
        OrderState currentState = orderStateFactory.getState(order.getStatus());
        currentState.processPayment(order, requestDto);
        return orderRepository.save(order);
    }
}