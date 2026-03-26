package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodJpaRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.order.vo.MemberSnapshot;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;

public abstract class AbstractPrePaymentState extends AbstractOrderState {

    protected final MemberPointService memberPointService;
    protected final PaymentMethodJpaRepository PaymentMethodJpaRepository;
    protected final UserJpaRepository UserJpaRepository;
    protected final OrderItemProcessorService orderItemProcessorService;
    protected final PromotionService promotionService;
    private final ApplicationEventPublisher eventPublisher;

    public AbstractPrePaymentState(MemberPointService memberPointService,
                                   PaymentMethodJpaRepository PaymentMethodJpaRepository,
                                   UserJpaRepository UserJpaRepository,
                                   OrderItemProcessorService orderItemProcessorService,
                                   PromotionService promotionService,
                                   ApplicationEventPublisher eventPublisher) {
        this.memberPointService = memberPointService;
        this.PaymentMethodJpaRepository = PaymentMethodJpaRepository;
        this.UserJpaRepository = UserJpaRepository;
        this.orderItemProcessorService = orderItemProcessorService;
        this.promotionService = promotionService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void update(OrderEntity OrderEntity, CreateOrderRequestDto dto) {
        Long brandId = OrderEntity.getBrand().getId();
        OrderItemProcessorService.ProcessResult result =
                orderItemProcessorService.processOrderItems(OrderEntity, dto.getItems(), brandId);
        OrderEntity.getItems().clear();
        OrderEntity.getItems().addAll(result.orderItems());
        OrderEntity.setTotalAmount(result.totalAmount());
        OrderEntity.setFinalAmount(result.totalAmount());
        OrderEntity.setPointsUsed(0L);
        OrderEntity.setDiscountAmount(BigDecimal.ZERO);
        OrderEntity.setStatus(dto.getStatus());
    }

    @Override
    public void processPayment(OrderEntity OrderEntity, ProcessPaymentRequestDto requestDto) {
        OrderStatus oldStatus = OrderEntity.getStatus();

        PaymentMethodEntity paymentMethodEntity = PaymentMethodJpaRepository.findByCode(requestDto.getPaymentMethod())
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + requestDto.getPaymentMethod()));
        OrderEntity.setPaymentMethod(paymentMethodEntity);
        UserEntity member;

        // 1. 計算會員點數折抵
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        Long pointsToUse = 0L;

        if (requestDto.getMemberId() != null) {
            member = UserJpaRepository.findByBrand_IdAndId(OrderEntity.getBrand().getId(), requestDto.getMemberId())
                    .filter(UserEntity -> UserEntity.getMemberProfile() != null)
                    .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + requestDto.getMemberId()));
            OrderEntity.setMember(member);

            if (member.getMemberProfile() != null) {
                MemberSnapshot snapshot = new MemberSnapshot(
                        member.getUserId(),
                        member.getMemberProfile().getFullName(),
                        member.getPrimaryPhone(),
                        member.getMemberProfile().getEmail()
                );
                OrderEntity.setMemberSnapshot(snapshot);
            }

            if (requestDto.getPointsToUse() > 0) {
                pointsToUse = requestDto.getPointsToUse();
                pointsDiscount = memberPointService.calculateDiscountAmount(pointsToUse, OrderEntity);
                memberPointService.usePoints(member, OrderEntity, pointsToUse);
            }
        }
        OrderEntity.setPointsUsed(pointsToUse);

        // 2. 【新增】計算促銷活動折扣
        BigDecimal promoDiscount = promotionService.calculateBestDiscount(OrderEntity);

        // 3. 計算總折扣與最終金額
        BigDecimal totalDiscount = pointsDiscount.add(promoDiscount);
        OrderEntity.setDiscountAmount(totalDiscount);

        BigDecimal finalAmount = OrderEntity.getTotalAmount().subtract(totalDiscount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }
        OrderEntity.setFinalAmount(finalAmount);

        // 狀態更新
        OrderEntity.setStatus(OrderStatus.PREPARING);
        eventPublisher.publishEvent(new OrderStateChangedEvent(OrderEntity, oldStatus, OrderStatus.PREPARING));
    }

    @Override
    public void cancel(OrderEntity OrderEntity) {
        OrderStatus oldStatus = OrderEntity.getStatus();
        OrderEntity.setStatus(OrderStatus.CANCELLED);
        eventPublisher.publishEvent(new OrderStateChangedEvent(OrderEntity, oldStatus, OrderStatus.CANCELLED));
    }

    @Override
    public void accept(OrderEntity OrderEntity) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法執行接單動作。");
    }
}