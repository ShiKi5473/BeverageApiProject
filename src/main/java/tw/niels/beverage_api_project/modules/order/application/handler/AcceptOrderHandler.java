package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.AcceptOrderCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;

/**
 * 店家接單 Handler
 *
 * 處理線上訂單的店家接單流程。
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate，並驗證訂單屬於呼叫者可操作的分店（StoreScope，不符 → 403；M-1 新增）
 * 2. 呼叫 order.accept()（AWAITING_ACCEPTANCE → PREPARING）
 * 3. 儲存 Aggregate
 * 4. 發佈領域事件
 *
 * 發佈事件：OrderStatusChangedEvent（AWAITING_ACCEPTANCE → PREPARING）
 */
@Component
public class AcceptOrderHandler {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AcceptOrderHandler(OrderRepository orderRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(AcceptOrderCommand command) {
        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 新增（M-1）：綁店員工只能接自己分店的訂單；在交易內、狀態變更之前檢查，不符回 403
        command.storeScope().verifyAccess(order.getStoreId());

        order.accept();

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
