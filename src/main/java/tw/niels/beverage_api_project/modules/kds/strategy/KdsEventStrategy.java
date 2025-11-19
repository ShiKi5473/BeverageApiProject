package tw.niels.beverage_api_project.modules.kds.strategy;

import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.kds.service.KdsService.KdsMessage;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

public interface KdsEventStrategy {
    /**
     * 此策略負責處理哪個訂單狀態
     */
    OrderStatus getHandledStatus();

    /**
     * 根據訂單建立對應的 KDS 訊息
     */
    KdsMessage createMessage(Order order);
}