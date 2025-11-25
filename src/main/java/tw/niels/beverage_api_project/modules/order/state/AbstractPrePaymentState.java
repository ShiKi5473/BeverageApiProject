package tw.niels.beverage_api_project.modules.order.state;

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
import tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService; // 新增匯入
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;

public abstract class AbstractPrePaymentState extends AbstractOrderState {

    protected final MemberPointService memberPointService;
    protected final PaymentMethodRepository paymentMethodRepository;
    protected final UserRepository userRepository;
    protected final OrderItemProcessorService orderItemProcessorService;
    protected final PromotionService promotionService;
    private final ApplicationEventPublisher eventPublisher;

    public AbstractPrePaymentState(MemberPointService memberPointService,
                                   PaymentMethodRepository paymentMethodRepository,
                                   UserRepository userRepository,
                                   OrderItemProcessorService orderItemProcessorService,
                                   PromotionService promotionService,
                                   ApplicationEventPublisher eventPublisher) {
        this.memberPointService = memberPointService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.userRepository = userRepository;
        this.orderItemProcessorService = orderItemProcessorService;
        this.promotionService = promotionService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void update(Order order, CreateOrderRequestDto dto) {
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

    @Override
    public void processPayment(Order order, ProcessPaymentRequestDto requestDto) {
        OrderStatus oldStatus = order.getStatus();

        PaymentMethodEntity paymentMethodEntity = paymentMethodRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));
        order.setPaymentMethod(paymentMethodEntity);
        User member = null;

        // 1. 計算會員點數折抵
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        Long pointsToUse = 0L;

        if (requestDto.getMemberId() != null) {
            member = userRepository.findByBrand_IdAndId(order.getBrand().getBrandId(), requestDto.getMemberId())
                    .filter(user -> user.getMemberProfile() != null)
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));
            order.setMember(member);

            if (member.getMemberProfile() != null) {
                MemberSnapshot snapshot = new MemberSnapshot(
                        member.getUserId(),
                        member.getMemberProfile().getFullName(),
                        member.getPrimaryPhone(),
                        member.getMemberProfile().getEmail()
                );
                order.setMemberSnapshot(snapshot);
            }

            if (requestDto.getPointsToUse() > 0) {
                pointsToUse = requestDto.getPointsToUse();
                pointsDiscount = memberPointService.calculateDiscountAmount(pointsToUse, order);
                memberPointService.usePoints(member, order, pointsToUse);
            }
        }
        order.setPointsUsed(pointsToUse);

        // 2. 【新增】計算促銷活動折扣
        BigDecimal promoDiscount = promotionService.calculateBestDiscount(order);

        // 3. 計算總折扣與最終金額
        BigDecimal totalDiscount = pointsDiscount.add(promoDiscount);
        order.setDiscountAmount(totalDiscount);

        BigDecimal finalAmount = order.getTotalAmount().subtract(totalDiscount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }
        order.setFinalAmount(finalAmount);

        // 狀態更新
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