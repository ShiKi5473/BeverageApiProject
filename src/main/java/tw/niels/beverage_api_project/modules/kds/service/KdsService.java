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
                // 現場/線上訂單付款完成
                logger.info("[KDS] 推送新訂單: #{} 至 {}", order.getOrderNumber(), kdsTopic);
                KdsOrderDto kdsDto;
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("NEW_ORDER", kdsOrderDto));
                break;

            case READY_FOR_PICKUP:
                // 【新增】KDS 製作完成
                logger.info("[KDS] 訂單製作完成: #{} 至 {}", order.getOrderNumber(), kdsTopic);

                // KDS 螢幕將訂單移至 "待取餐"
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("MOVE_TO_PICKUP", kdsOrderDto));

                // (未來) 在此觸發線上點餐顧客的「取餐通知」
                // customerNotificationService.notifyCustomer(order, "您的餐點已可取用！");
                break;

            case CLOSED:
                // 【修改】POS 確認顧客已取餐
                logger.info("[KDS] 訂單已結案: #{} 至 {}", order.getOrderNumber(), kdsTopic);

                // KDS 螢幕可以將訂單從 "待取餐" 列表中移除
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("REMOVE_FROM_PICKUP", kdsOrderDto));
                break;

            case CANCELLED:
                // 訂單在任何階段被取消
                logger.info("[KDS] 推送取消訂單: #{} 至 {}", order.getOrderNumber(), kdsTopic);
                messagingTemplate.convertAndSend(kdsTopic,
                        new KdsMessage("CANCEL_ORDER", kdsOrderDto));
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
