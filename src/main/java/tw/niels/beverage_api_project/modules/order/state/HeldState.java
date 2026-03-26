// 新增的：HeldState.java
package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodJpaRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

@Component("HELD") // 【注意】Bean Name
public class HeldState extends AbstractPrePaymentState { // 【注意】繼承新類別

    // 建構子只需呼叫 super()
    public HeldState(MemberPointService memberPointService,
                     PaymentMethodJpaRepository PaymentMethodJpaRepository,
                     UserJpaRepository UserJpaRepository,
                     OrderItemProcessorService orderItemProcessorService,
                     PromotionService promotionService,
                     ApplicationEventPublisher  eventPublisher) {
        super(memberPointService, PaymentMethodJpaRepository, UserJpaRepository, orderItemProcessorService, promotionService, eventPublisher);
    }

    @Override
    protected OrderStatus getStatus() {
        return OrderStatus.HELD;
    }

}