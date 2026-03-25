package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrderQuery;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrdersQuery;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;

import java.util.List;

/**
 * 訂單查詢 Handler（唯讀）
 *
 * 處理訂單的各種查詢需求，所有操作標記為 readOnly。
 *
 * 處理流程：
 * 1. 接收查詢條件（單一訂單 ID 或列表篩選）
 * 2. 透過 OrderRepository 查詢 Domain Model
 * 3. 回傳 Domain Order（由 Controller 負責轉為 DTO）
 *
 * 備註：查詢時需進行 OrderStatus enum 轉換（JPA ↔ Domain），
 * 待 Phase 3 Infrastructure 分離後統一處理。
 */
@Component
@Transactional(readOnly = true)
public class OrderQueryHandler {

    private final OrderRepository orderRepository;

    public OrderQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order handle(GetOrderQuery query) {
        return orderRepository.findByBrandIdAndId(query.brandId(), query.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + query.orderId()));
    }

    public List<Order> handle(GetOrdersQuery query) {
        if (query.status() != null) {
            // 轉換 Enum
            tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus domainStatus = 
                tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus.valueOf(query.status().name());
            return orderRepository.findByBrandIdAndStoreIdAndStatus(
                query.brandId(), query.storeId(), domainStatus
            );
        } else {
            return orderRepository.findByBrandIdAndStoreId(query.brandId(), query.storeId());
        }
    }
}
