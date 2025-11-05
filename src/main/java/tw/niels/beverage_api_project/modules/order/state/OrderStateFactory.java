package tw.niels.beverage_api_project.modules.order.state;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Component
public class OrderStateFactory {

    private final ApplicationContext context;

    public OrderStateFactory(ApplicationContext context) {
        this.context = context;
    }

    /**
     * 根據 Enum 取得對應的狀態 Bean
     */
    public OrderState getState(OrderStatus status) {
        // 利用我們在 @Component 中設定的 Bean Name (與 Enum 名稱一致)
        return context.getBean(status.name(), OrderState.class);
    }
}