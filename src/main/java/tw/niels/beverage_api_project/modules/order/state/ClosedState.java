package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Component("CLOSED")
public class ClosedState extends AbstractOrderState {
    @Override
    protected OrderStatus getStatus() { return OrderStatus.CLOSED; }
}