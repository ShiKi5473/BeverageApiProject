package tw.niels.beverage_api_project.modules.order.state;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

/**
 * 抽象基礎狀態
 * 預設所有動作都「不支援」，拋出例外。
 * 具體的狀態類別只需要覆寫 (override) 它們支援的動作。
 */
public abstract class AbstractOrderState implements OrderState {

    // 讓子類別可以取得目前狀態
    protected abstract OrderStatus getStatus();

    @Override
    public void update(Order order, CreateOrderRequestDto dto) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法被修改內容。");
    }

    @Override
    public void processPayment(Order order, ProcessPaymentRequestDto requestDto) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法進行結帳。");
    }

    @Override
    public void complete(Order order) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法標記為完成。");
    }

    @Override
    public void cancel(Order order) {
        throw new BadRequestException("訂單狀態為 " + getStatus() + "，無法取消。");
    }
}