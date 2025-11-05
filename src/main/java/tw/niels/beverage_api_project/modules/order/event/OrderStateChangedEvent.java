// 建議放在：tw/niels/beverage_api_project/modules/order/event/
package tw.niels.beverage_api_project.modules.order.event;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 訂單狀態變更事件
 * (繼承 ApplicationEvent 是一種做法，但直接當 POJO 也可以)
 */
public class OrderStateChangedEvent {
    private final Order order;
    private final OrderStatus oldStatus; // 變更前的狀態 (可選)
    private final OrderStatus newStatus; // 變更後的狀態

    public OrderStateChangedEvent(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        this.order = order;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    // Getters
    public Order getOrder() { return order; }
    public OrderStatus getOldStatus() { return oldStatus; }
    public OrderStatus getNewStatus() { return newStatus; }
}