package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateOrderStatusCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 訂單狀態變更 Handler
 *
 * 根據目標狀態路由至對應的 Domain 方法，統一處理手動狀態轉換。
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate
 * 2. 根據目標狀態呼叫對應 Domain 方法：
 *    - HELD → order.hold()
 *    - READY_FOR_PICKUP → order.markReadyForPickup()
 *    - CLOSED → order.close()
 *    - CANCELLED → order.cancel()
 * 3. 儲存 Aggregate
 * 4. 發佈領域事件
 *
 * 發佈事件：
 * - OrderStatusChangedEvent（HELD / READY_FOR_PICKUP）
 * - OrderClosedEvent（CLOSED）
 * - OrderCancelledEvent（CANCELLED）
 */
@Component
public class UpdateOrderStatusHandler {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateOrderStatusHandler(OrderRepository orderRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateOrderStatusCommand command) {
        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 根據目標狀態調用對應的領域方法
        switch (command.status()) {
            case HELD -> order.hold();
            case READY_FOR_PICKUP -> order.markReadyForPickup();
            case CLOSED -> order.close(0L); // 點數由專門的結帳/完成流程處理
            case CANCELLED -> order.cancel();
            default -> throw new IllegalArgumentException("不支援的手動狀態變更: " + command.status());
        }

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
