package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateHeldOrderCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;
import tw.niels.beverage_api_project.modules.product.domain.model.ProductVariant;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新暫存訂單 Handler
 *
 * 處理 PENDING 或 HELD 狀態訂單的品項修改。
 *
 * 處理流程：
 * 1. 載入訂單 Aggregate
 * 2. 將新的 OrderItemDto 轉為 Domain OrderItem（含 ProductSnapshot）
 * 3. 計算新的總金額
 * 4. 呼叫 order.updateItems() 替換品項
 * 5. 若需要，執行狀態轉換（PENDING → HELD）
 * 6. 儲存 Aggregate
 * 7. 發佈領域事件
 *
 * 發佈事件：OrderStatusChangedEvent（若狀態有變更）
 */
@Component
public class UpdateHeldOrderHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateHeldOrderHandler(OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(UpdateHeldOrderCommand command) {
        Order order = orderRepository.findByBrandIdAndId(command.brandId(), command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + command.orderId()));

        // 1. 處理新項品
        List<OrderItem> newItems = processItems(command.brandId(), command.items());
        Money newTotalAmount = newItems.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);

        // 2. 更新 Aggregate
        order.updateItems(newItems, newTotalAmount);

        // 3. 處理狀態變動 (PENDING <-> HELD)
        if (command.status().name().equals("HELD") && order.getStatus().name().equals("PENDING")) {
            order.hold();
        }

        orderRepository.save(order);
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);
    }

    private List<OrderItem> processItems(Long brandId, List<OrderItemDto> itemDtos) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDto dto : itemDtos) {
            Product product = productRepository.findProductById(brandId, dto.productId())
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在: " + dto.productId()));
            
            ProductVariant variant = product.getVariants().stream()
                .filter(v -> v.getId().equals(dto.variantId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("規格不存在: " + dto.variantId()));

            ProductSnapshot snapshot = new ProductSnapshot(
                product.getId(),
                product.getName(),
                variant.getPrice(),
                "Category",
                variant.getName()
            );

            OrderItem item = OrderItem.create(
                product.getId(),
                variant.getId(),
                dto.quantity(),
                variant.getPrice(),
                dto.notes(),
                dto.optionIds() != null ? dto.optionIds() : List.of(),
                snapshot
            );
            items.add(item);
        }
        return items;
    }
}
