package tw.niels.beverage_api_project.modules.order.state;

import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.ProcessPaymentRequestDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;

/**
 * 狀態模式介面
 * 定義了訂單在不同狀態下可以執行的「動作」。
 */
public interface OrderState {

    /**
     * 動作：更新訂單內容
     * (僅 HELD 狀態可執行)
     */
    void update(Order order, CreateOrderRequestDto dto);

    /**
     * 動作：處理付款
     * (僅 PENDING 和 HELD 狀態可執行)
     */
    void processPayment(Order order, ProcessPaymentRequestDto requestDto);

    /**
     * 動作：完成訂單
     * (僅 PREPARING 狀態可執行)
     */
    void complete(Order order);

    /**
     * 動作：取消訂單
     * (PENDING, HELD, PREPARING 狀態可執行)
     */
    void cancel(Order order);

    /**
     * 動作：店家確認接單
     * (僅 AWAITING_ACCEPTANCE 狀態可執行)
     */
    void accept(Order order);

}