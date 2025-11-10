package tw.niels.beverage_api_project.modules.order.enums;

public enum OrderStatus {
    PENDING,          // 現場點餐 (未付款)
    AWAITING_ACCEPTANCE, // 線上點餐 (已付款，等待店家接單)
    PREPARING,
    READY_FOR_PICKUP,
    CLOSED, //訂單完成取餐，建檔
    CANCELLED, //取消訂單
    HELD //暫存訂單
}