package tw.niels.beverage_api_project.modules.kds.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.kds.service.KdsService.KdsMessage;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Component
public class ClosedStrategy implements KdsEventStrategy {
    @Override
    public OrderStatus getHandledStatus() { return OrderStatus.CLOSED; }

    @Override
    public KdsMessage createMessage(Order order) {
        return new KdsMessage("REMOVE_FROM_PICKUP", KdsOrderDto.fromEntity(order));
    }
}