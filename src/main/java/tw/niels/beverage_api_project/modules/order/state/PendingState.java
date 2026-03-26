// 修改後的：PendingState.java
package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodJpaRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderItemProcessorService;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

@Component("PENDING")
public class PendingState extends AbstractPrePaymentState {

    // 建構子只需呼叫 super()
    public PendingState(MemberPointService memberPointService,
                        PaymentMethodJpaRepository PaymentMethodJpaRepository,
                        UserJpaRepository UserJpaRepository,
                        OrderItemProcessorService orderItemProcessorService,
                        PromotionService promotionService,
                        ApplicationEventPublisher eventPublisher) {
        super(memberPointService, PaymentMethodJpaRepository, UserJpaRepository, orderItemProcessorService, promotionService, eventPublisher);    }

    @Override
    protected OrderStatus getStatus() {
        return OrderStatus.PENDING;
    }

}