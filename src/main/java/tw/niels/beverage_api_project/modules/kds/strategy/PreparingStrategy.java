package tw.niels.beverage_api_project.modules.kds.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.kds.service.KdsService.KdsMessage;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Component
public class PreparingStrategy implements KdsEventStrategy {
    @Override
    public OrderStatus getHandledStatus() { return OrderStatus.PREPARING; }

    @Override
    public KdsMessage createMessage(Order order) {
        return new KdsMessage("NEW_ORDER", KdsOrderDto.fromEntity(order));
    }
}