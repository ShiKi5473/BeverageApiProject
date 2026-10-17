package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateOrderStatusCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;

/**
 * 訂單狀態變更 Handler
 *
 * 根據目標狀態路由至對應的 Domain 方法，統一處理手動狀態轉換。
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate，並驗證訂單屬於呼叫者可操作的分店（StoreScope，不符 → 403）
 * 2. 根據目標狀態呼叫對應 Domain 方法：
 *    - HELD → order.hold()
 *    - PREPARING → order.accept()（線上訂單接單，與 PATCH /{id}/accept 相同）
 *    - READY_FOR_PICKUP → order.markReadyForPickup()
 *    - CLOSED → 依品牌 earnRate 計算累積點數後 order.close(points)
 *    - CANCELLED → order.cancel()
 *    - 其他（PENDING、AWAITING_ACCEPTANCE）→ 400
 * 3. 儲存並發佈領域事件
 *
 * 發佈事件：
 * - OrderStatusChangedEvent（HELD / PREPARING / READY_FOR_PICKUP）
 * - OrderClosedEvent（CLOSED，攜帶 pointsEarned 供 Member 累點）
 * - OrderCancelledEvent（CANCELLED）
 *
 * 修改說明：
 * - CLOSED 時改為實際計算累積點數（原本固定 close(0L)，會員永遠不會累點，B-2）；
 * - 不支援的狀態改拋 BadRequestException（400），原本為 IllegalArgumentException（B-9）；
 * - 新增分店存取檢查（M-1）：原本只依品牌載入訂單，綁定分店 1 的員工可取消 / 結案分店 2 的訂單。
 */
@Component
public class UpdateOrderStatusHandler {

    private final OrderRepository orderRepository;
    private final OrderCheckoutService orderCheckoutService;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateOrderStatusHandler(OrderRepository orderRepository,
                                    OrderCheckoutService orderCheckoutService,
                                    ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderCheckoutService = orderCheckoutService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateOrderStatusCommand command) {
        if (command.status() == null) {
            throw new BadRequestException("訂單狀態不可為空");
        }

        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 新增（M-1）：綁店員工只能變更自己分店的訂單；在交易內、任何狀態變更之前檢查，不符回 403
        command.storeScope().verifyAccess(order.getStoreId());

        // 根據目標狀態調用對應的領域方法（狀態前置條件由 Domain 驗證，違反時 422）
        switch (command.status()) {
            case HELD -> order.hold();
            case PREPARING -> order.accept();
            case READY_FOR_PICKUP -> order.markReadyForPickup();
            case CLOSED -> {
                // 修改：依品牌積點規則計算累積點數（訪客訂單為 0），由 OrderClosedEvent 通知 Member 累點
                long pointsEarned = orderCheckoutService.calculateEarnedPoints(
                    order.getBrandId(), order.getMemberId(), order.getFinalAmount());
                order.close(pointsEarned);
            }
            case CANCELLED -> order.cancel();
            default -> throw new BadRequestException("不支援的手動狀態變更: " + command.status());
        }

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
