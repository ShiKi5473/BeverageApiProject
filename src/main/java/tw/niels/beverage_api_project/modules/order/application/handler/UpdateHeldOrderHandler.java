package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateHeldOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;

/**
 * 更新暫存訂單 Handler
 *
 * 處理 PENDING 或 HELD 狀態訂單的品項修改。
 *
 * 處理流程：
 * 1. 驗證目標狀態（僅允許 PENDING / HELD）並載入訂單 Aggregate，
 *    驗證訂單屬於呼叫者可操作的分店（StoreScope，不符 → 403）
 * 2. 以 OrderItemFactory 批次建立新品項（真實快照、選項加價、備註轉為純文字）
 * 3. 計算新的總金額，呼叫 order.updateItems() 替換品項
 * 4. 若目標為 HELD 且目前為 PENDING，執行 order.hold()
 * 5. 儲存並發佈領域事件
 *
 * 發佈事件：OrderStatusChangedEvent（若狀態有變更）
 *
 * 修改說明（P0-2）：移除逐項查詢商品、分類寫死 "Category" 的 processItems，改用 OrderItemFactory。
 * 修改說明（M-1）：新增分店存取檢查，原本只依品牌載入訂單，綁店員工可修改其他分店的暫存訂單。
 */
@Component
public class UpdateHeldOrderHandler {

    private final OrderRepository orderRepository;
    private final OrderItemFactory orderItemFactory;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateHeldOrderHandler(OrderRepository orderRepository,
                                  OrderItemFactory orderItemFactory,
                                  ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemFactory = orderItemFactory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateHeldOrderCommand command) {
        // 新增：目標狀態只能是 PENDING 或 HELD，其餘回 400（原本會被靜默忽略）
        OrderStatus targetStatus = command.status();
        if (targetStatus != null && targetStatus != OrderStatus.PENDING && targetStatus != OrderStatus.HELD) {
            throw new BadRequestException("更新暫存訂單僅允許 PENDING 或 HELD 狀態，收到：" + targetStatus);
        }

        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 新增（M-1）：綁店員工只能修改自己分店的訂單；在交易內、修改品項之前檢查，不符回 403
        command.storeScope().verifyAccess(order.getStoreId());

        // 1. 批次建立新品項
        List<OrderItem> newItems = orderItemFactory.createItems(command.brandId(), command.items());
        Money newTotalAmount = newItems.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);

        // 2. 更新 Aggregate（Domain 驗證狀態必須為 PENDING / HELD）
        order.updateItems(newItems, newTotalAmount);

        // 3. 處理狀態變動（PENDING → HELD；HELD → PENDING 在 Domain 中沒有對應操作，維持 HELD）
        if (targetStatus == OrderStatus.HELD
                && order.getStatus() == tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus.PENDING) {
            order.hold();
        }

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
