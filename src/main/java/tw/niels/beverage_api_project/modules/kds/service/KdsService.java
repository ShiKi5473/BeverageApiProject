// 建議放在：tw/niels/beverage_api_project/modules/kds/service/
package tw.niels.beverage_api_project.modules.kds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Service
public class KdsService {

    private static final Logger logger = LoggerFactory.getLogger(KdsService.class);

    // 注入 KDS 需要的依賴，例如 WebSocket 傳訊服務
    // @Autowired
    // private SimpMessagingTemplate messagingTemplate;

    /**
     * 【關鍵】這就是觀察者！
     * 此方法會 "訂閱" OrderStateChangedEvent 事件。
     * 當任何地方 (例如 OrderState) publish 了這個事件，Spring 會自動呼叫此方法。
     */
    @EventListener
    public void handleOrderStateChange(OrderStateChangedEvent event) {

        Order order = event.getOrder();
        OrderStatus newStatus = event.getNewStatus();

        logger.info("KDS 收到訂單 #{} 狀態變更為: {}", order.getOrderNumber(), newStatus);

        if (newStatus == OrderStatus.PREPARING) {
            // 這是一筆新訂單
            // 1. (可選) 將訂單資料存入 KDS 專用的資料表
            // 2. 透過 WebSocket 將新訂單推送到廚房的 KDS 螢幕
            // messagingTemplate.convertAndSend("/topic/kds/" + order.getStore().getStoreId(), orderDto);
            logger.info("KDS: 推送新訂單 #{} 到廚房。", order.getOrderNumber());

        } else if (newStatus == OrderStatus.CANCELLED) {
            // 這筆訂單被取消了
            // 1. (可選) 更新 KDS 資料表狀態
            // 2. 透過 WebSocket 通知 KDS 螢幕將此訂單移除或標記為取消
            logger.info("KDS: 移除已取消訂單 #{}。", order.getOrderNumber());

        } else if (newStatus == OrderStatus.COMPLETED) {
            // 這筆訂單已完成
            // 1. (可選) 更新 KDS 資料表狀態
            // 2. 透過 WebSocket 通知 KDS 螢幕將此訂單標記為完成 (或從「製作中」移到「待取餐」)
            logger.info("KDS: 訂單 #{} 已完成，移至待取餐。", order.getOrderNumber());
        }

        // 注意：PENDING 或 HELD 狀態可能不需要通知 KDS，因為廚房還不用動作
    }
}