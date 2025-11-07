// 建議放在：tw/niels/beverage_api_project/modules/kds/service/
package tw.niels.beverage_api_project.modules.kds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.kds.dto.KdsOrderDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.event.OrderStateChangedEvent;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Service
public class KdsService {

    private static final Logger logger = LoggerFactory.getLogger(KdsService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public  KdsService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }


    /**
     * 【關鍵】這就是觀察者！
     * 此方法會 "訂閱" OrderStateChangedEvent 事件。
     * 當任何地方 (例如 OrderState) publish 了這個事件，Spring 會自動呼叫此方法。
     */
    @EventListener
    public void handleOrderStateChange(OrderStateChangedEvent event) {
        Order order = event.getOrder();
        OrderStatus newStatus = event.getNewStatus();

        String kdsTopic = "/topic/kds/store/" + order.getStore().getStoreId();
        KdsOrderDto kdsOrderDto = KdsOrderDto.fromEntity(order);
        switch (newStatus) {
            case PREPARING:
                logger.info("[KDS] 推送新訂單: #{} 至 {}", order.getOrderNumber(), kdsTopic);

                // 【關鍵】透過 WebSocket 推送 "新訂單" 訊息
                // 前端 KDS 訂閱 kdsTopic 後，就會收到 "NEW_ORDER" 和訂單資料
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("NEW_ORDER", kdsOrderDto));
                break;

            case CANCELLED:
                if (event.getOldStatus() == OrderStatus.PREPARING) {
                    logger.info("[KDS] 推送取消訂單: #{} 至 {}", order.getOrderNumber(), kdsTopic);

                    // 【關鍵】推送 "取消訂單" 訊息
                    messagingTemplate.convertAndSend(kdsTopic,
                            new KdsMessage("CANCEL_ORDER", kdsOrderDto));
                }
                break;

            case COMPLETED:
                logger.info("[KDS] 推送完成訂單: #{} 至 {}", order.getOrderNumber(), kdsTopic);

                // 【關鍵】推送 "完成訂單" 訊息 (KDS 可移至待取餐)
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("COMPLETE_ORDER", kdsOrderDto));
                break;

            default:
                break;
        }


    }
    private static class KdsMessage {
        private String action; // "NEW_ORDER", "CANCEL_ORDER", "COMPLETE_ORDER"
        private KdsOrderDto payload;

        public KdsMessage(String action, KdsOrderDto payload) {
            this.action = action;
            this.payload = payload;
        }

        public String getAction() { return action; }
        public KdsOrderDto getPayload() { return payload; }
    }

        // 注意：PENDING 或 HELD 狀態可能不需要通知 KDS，因為廚房還不用動作
    }
