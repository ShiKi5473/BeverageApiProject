// 建議放在：tw/niels/beverage_api_project/modules/order/event/
package tw.niels.beverage_api_project.modules.order.event;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 訂單狀態變更事件
 * (繼承 ApplicationEvent 是一種做法，但直接當 POJO 也可以)
 *
 * @param oldStatus 變更前的狀態 (可選)
 * @param newStatus 變更後的狀態
 */
public record OrderStateChangedEvent(
        Order order,
        OrderStatus oldStatus,
        OrderStatus newStatus
) {
}