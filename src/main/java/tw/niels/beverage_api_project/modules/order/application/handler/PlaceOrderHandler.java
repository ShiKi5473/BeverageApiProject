package tw.niels.beverage_api_project.modules.order.application.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOrderCommand;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;
import tw.niels.beverage_api_project.modules.product.domain.model.ProductVariant;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.store.service.StoreService;
import tw.niels.beverage_api_project.modules.user.service.UserService;

/**
 * 建立訂單 Handler
 *
 * 處理 POS 或線上渠道的訂單建立流程。
 *
 * 處理流程：
 * 1. 驗證店家與員工資料是否存在
 * 2. 產生每日店家序號與訂單號碼
 * 3. 將 OrderItemDto 轉為 Domain OrderItem（查詢商品與規格）
 * 4. 呼叫 Order.place() 工廠方法建立 Aggregate
 * 5. 若目標狀態為 HELD，執行 order.hold() 轉換
 * 6. 透過 OrderRepository 儲存 Aggregate
 * 7. 發佈領域事件
 *
 * 發佈事件：OrderPlacedEvent（訂單建立成功後）
 */
@Component
public class PlaceOrderHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderNumberService orderNumberService;
    private final StoreService storeService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceOrderHandler(OrderRepository orderRepository,
                             ProductRepository productRepository,
                             OrderNumberService orderNumberService,
                             StoreService storeService,
                             UserService userService,
                             ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderNumberService = orderNumberService;
        this.storeService = storeService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 執行建立訂單流程
     */
    @Transactional
    public Long handle(PlaceOrderCommand command) {
        // 1. 驗證基礎資料
        storeService.getStore(command.brandId(), command.storeId());
        if (command.userId() != null) {
            userService.getUser(command.brandId(), command.userId());
        }

        // 2. 產生成訂單號碼
        long sequence = orderNumberService.getNextStoreDailySequence(command.storeId());
        String orderNumStr = String.format("A%03d", sequence);
        OrderNumber orderNumber = OrderNumber.of(orderNumStr);

        // 3. 處理訂單品項 (將 DTO 轉為 Domain Entity)
        List<OrderItem> domainItems = processItems(command.brandId(), command.items());

        // 4. 建立訂單 Aggregate
        Order order = Order.place(
            command.brandId(),
            command.storeId(),
            command.userId(),
            orderNumber,
            domainItems,
            null // customerNote 暫無
        );

        // 5. 根據請求狀態調整 (PENDING 為預設，若為 HELD 則轉換)
        if (command.status().name().equals("HELD")) {
            order.hold();
        }

        // 6. 儲存
        orderRepository.save(order);

        // 7. 發布領域事件
        order.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return order.getId();
    }

    private List<OrderItem> processItems(Long brandId, List<OrderItemDto> itemDtos) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDto dto : itemDtos) {
            // 查詢商品、規格、選項 (Domain Model)
            Product product = productRepository.findProductById(brandId, dto.productId())
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在: " + dto.productId()));
            
            ProductVariant variant = product.getVariants().stream()
                .filter(v -> v.getId().equals(dto.variantId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("規格不存在: " + dto.variantId()));

            // 建立品項
            OrderItem item = OrderItem.create(
                product.getId(),
                variant.getId(),
                dto.quantity(),
                variant.getPrice(), // 暫不計入選項
                dto.notes(),
                dto.optionIds() != null ? dto.optionIds() : List.<Long>of(),
                new ProductSnapshot(product.getId(), "Snapshot Name", variant.getPrice(), "Snapshot Category", "Snapshot Variant") // 暫無 Snapshot 取值邏輯，傳入假資料
            );
            items.add(item);
        }
        return items;
    }
}
