// 新檔案：AbstractPrePaymentState.java
package tw.niels.beverage_api_project.modules.order.state;

// ... 匯入 ...
import org.springframework.context.ApplicationEventPublisher;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;

// 注意：這個類別「不」需要 @Component，因為它不會被 Spring 直接實例化
public abstract class AbstractPrePaymentState extends AbstractOrderState {

    protected final MemberPointService memberPointService;
    protected final PaymentMethodRepository paymentMethodRepository;
    protected final UserRepository userRepository;
    protected final OrderItemProcessorService orderItemProcessorService;
    private final ApplicationEventPublisher eventPublisher;

    public AbstractPrePaymentState(MemberPointService memberPointService,
                                   PaymentMethodRepository paymentMethodRepository,
                                   UserRepository userRepository,
                                   OrderItemProcessorService orderItemProcessorService,
                                   ApplicationEventPublisher eventPublisher) {
        this.memberPointService = memberPointService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.userRepository = userRepository;
        this.orderItemProcessorService = orderItemProcessorService;
        this.eventPublisher = eventPublisher;
    }

    // 移入 update 實作
    @Override
    public void update(Order order, CreateOrderRequestDto dto) {
        // ... (從 PendingState 搬過來的完整邏輯) ...
        Long brandId = order.getBrand().getBrandId();
        OrderItemProcessorService.ProcessedItemsResult result =
                orderItemProcessorService.processOrderItems(order, dto.getItems(), brandId);
        order.getItems().clear();
        order.getItems().addAll(result.orderItems);
        order.setTotalAmount(result.totalAmount);
        order.setFinalAmount(result.totalAmount);
        order.setPointsUsed(0L);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(dto.getStatus());
    }

    // 移入 processPayment 實作
    @Override
    public void processPayment(Order order, ProcessPaymentRequestDto requestDto) {
        OrderStatus oldStatus = order.getStatus();

        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));
        order.setPaymentMethod(paymentMethodEntity);
        User member = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long pointsToUse = 0L;

        if (requestDto.getMemberId() != null) {
            member = userRepository.findById(requestDto.getMemberId())
                    .filter(user -> user.getMemberProfile() != null && user.getBrand().getBrandId().equals(order.getBrand().getBrandId()))
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));

            order.setMember(member);

            if (requestDto.getPointsToUse() > 0) {
                pointsToUse = requestDto.getPointsToUse();
                discountAmount = memberPointService.calculateDiscountAmount(pointsToUse);
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
        order.setStatus(OrderStatus.PREPARING);
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.PREPARING));
    }

    @Override
    public void cancel(Order order) {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(order, oldStatus, OrderStatus.CANCELLED));
    }
    @Override
    public void accept(Order order) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法執行接單動作。");
    }
}