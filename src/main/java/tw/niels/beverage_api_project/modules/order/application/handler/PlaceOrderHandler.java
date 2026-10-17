package tw.niels.beverage_api_project.modules.order.application.handler;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

/**
 * 建立訂單 Handler（POS 現場：待付款或暫存）
 *
 * 處理流程：
 * 1. 驗證目標狀態（僅允許 PENDING / HELD）與店家
 * 2. 以 OrderItemFactory 批次建立品項（真實快照、選項加價、Jsoup 清洗備註）
 * 3. 產生訂單號碼（{storeId}-{yyyyMMdd}-{seq:04d}）與預先指派的訂單 ID
 * 4. 呼叫 Order.place() 建立 Aggregate；若目標為 HELD 再執行 order.hold()
 * 5. 儲存並發佈領域事件
 *
 * 發佈事件：OrderPlacedEvent（＋ HELD 時的 OrderStatusChangedEvent）
 *
 * 修改說明（P0-2、P0-3、P0-4）：
 * - 移除逐項查詢商品、寫入假快照（"Snapshot Name"）的 processItems，改用 OrderItemFactory；
 * - 訂單號碼由 "A%03d"（每店每日重置，違反品牌唯一鍵）改為 OrderNumberService.nextOrderNumber()；
 * - 訂單 ID 改由 OrderRepository.nextId() 預先指派，事件的 orderId 不再為 null；
 * - 不支援的初始狀態（如 PREPARING）改回 400，原本會被靜默當成 PENDING。
 */
@Component
public class PlaceOrderHandler {

    private final OrderRepository orderRepository;
    private final OrderItemFactory orderItemFactory;
    private final OrderNumberService orderNumberService;
    private final StoreService storeService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceOrderHandler(OrderRepository orderRepository,
                             OrderItemFactory orderItemFactory,
                             OrderNumberService orderNumberService,
                             StoreService storeService,
                             ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemFactory = orderItemFactory;
        this.orderNumberService = orderNumberService;
        this.storeService = storeService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 執行建立訂單流程
     *
     * @return 新訂單 ID
     */
    @Transactional
    public Long handle(PlaceOrderCommand command) {
        // 1. 驗證目標狀態：POS 建單只允許 PENDING（待付款）或 HELD（暫存）
        OrderStatus targetStatus = command.status() != null ? command.status() : OrderStatus.PENDING;
        if (targetStatus != OrderStatus.PENDING && targetStatus != OrderStatus.HELD) {
            throw new BadRequestException("建立訂單僅允許 PENDING 或 HELD 狀態，收到：" + targetStatus);
        }
        // 店家必須屬於本品牌（不存在則 404）
        storeService.getStore(command.brandId(), command.storeId());

        // 2. 批次建立品項（取代原本逐項查詢與假快照）
        List<OrderItem> domainItems = orderItemFactory.createItems(command.brandId(), command.items());

        // 3. 產生訂單號碼（品牌內唯一）與預先指派的訂單 ID
        OrderNumber orderNumber = OrderNumber.of(orderNumberService.nextOrderNumber(command.storeId()));
        Long orderId = orderRepository.nextId();

        // 4. 建立訂單 Aggregate（初始為 PENDING）
        Order order = Order.place(
            orderId,
            command.brandId(),
            command.storeId(),
            command.userId(),
            orderNumber,
            domainItems,
            null // POS 建單沒有顧客備註
        );

        // 5. 目標為 HELD 時轉為暫存
        if (targetStatus == OrderStatus.HELD) {
            order.hold();
        }

        // 6. 儲存
        orderRepository.save(order);

        // 7. 發布領域事件（AFTER_COMMIT 監聽者會在交易提交後收到）
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return order.getId();
    }
}
