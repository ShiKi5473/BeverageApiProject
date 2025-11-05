package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;

@Component("PENDING") // 使用 Enum 名稱作為 Spring Bean 名稱
public class PendingState extends AbstractOrderState {

    private final MemberPointService memberPointService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;
    private final OrderItemProcessorService  orderItemProcessorService;

    public PendingState(MemberPointService memberPointService, PaymentMethodRepository paymentMethodRepository,
                        UserRepository userRepository, OrderItemProcessorService orderItemProcessorService) {
        this.memberPointService = memberPointService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.userRepository = userRepository;
        this.orderItemProcessorService = orderItemProcessorService; // 【新增】
    }

    // 讓 AbstractOrderState 知道目前狀態
    @Override
    protected OrderStatus getStatus() { return OrderStatus.PENDING; } // 或 HELD

    /**
     * PENDING/HELD 狀態「支援」更新
     */
    @Override
    public void update(Order order, CreateOrderRequestDto dto) {
        // 1. 取得 Brand ID
        Long brandId = order.getBrand().getBrandId();

        // 2. 使用共用服務處理新的品項
        OrderItemProcessorService.ProcessedItemsResult result =
                orderItemProcessorService.processOrderItems(order, dto.getItems(), brandId);

        // 3. 【關鍵】替換品項
        // 必須先清空，JPA/Hibernate 才會刪除舊的 (orphanRemoval=true)
        order.getItems().clear();
        order.getItems().addAll(result.orderItems);

        // 4. 重設計價
        order.setTotalAmount(result.totalAmount);
        order.setFinalAmount(result.totalAmount); // 重設最終金額
        order.setPointsUsed(0L); // 清空已用點數
        order.setDiscountAmount(BigDecimal.ZERO); // 清空折扣

        // 5. 狀態維持 (使用者可能只是按「儲存」，或按「結帳」)
        // DTO 上的 status 會是 PENDING 或 HELD
        order.setStatus(dto.getStatus());
    }
    /**
     * PENDING/HELD 狀態「支援」付款
     */
    @Override
    public void processPayment(Order order, ProcessPaymentRequestDto requestDto) {
        // --- 這是從 OrderService.processPayment 搬移過來的邏輯 ---
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
    }

    /**
     * PENDING/HELD 狀態「支援」取消
     */
    @Override
    public void cancel(Order order) {
        // PENDING/HELD 狀態取消時，不需要退還點數 (因為還沒付款)
        order.setStatus(OrderStatus.CANCELLED);
    }
}