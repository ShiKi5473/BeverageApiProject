// 新增的：HeldState.java
package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

@Component("HELD") // 【注意】Bean Name
public class HeldState extends AbstractPrePaymentState { // 【注意】繼承新類別

    // 建構子只需呼叫 super()
    public HeldState(MemberPointService memberPointService,
                     PaymentMethodRepository paymentMethodRepository,
                     UserRepository userRepository,
                     OrderItemProcessorService orderItemProcessorService,
                     ApplicationEventPublisher  eventPublisher) {
        super(memberPointService, paymentMethodRepository, userRepository, orderItemProcessorService, eventPublisher);
    }

    @Override
    protected OrderStatus getStatus() {
        return OrderStatus.HELD;
    }

    // update(), processPayment(), cancel() 都不用寫了！
}