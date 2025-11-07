package tw.niels.beverage_api_project.modules.kds.dto;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 專門用於推送到 KDS (廚房顯示系統) 的訂單 DTO。
 * 僅包含 KDS 必要的資訊 (例如：品項、選項、備註)，
 * 移除了所有財務和會員相關的敏感資料。
 */
public class KdsOrderDto {

    private final Long orderId;
    private final String orderNumber;
    private final OrderStatus status;
    private final Date orderTime;
    private final String customerNote; // 訂單層級的備註
    private final List<KdsOrderItemDto> items;

    // 內部靜態類別 (Inner Static Class) - KdsOrderItemDto
    public static class KdsOrderItemDto {
        private final Long orderItemId;
        private final String productName;
        private final Integer quantity;
        private final String notes; // 品項層級的備註 (例如：少冰)
        private final List<KdsOrderOptionDto> options;

        public KdsOrderItemDto(OrderItem item) {
            this.orderItemId = item.getOrderItemId();
            this.productName = item.getProduct().getName();
            this.quantity = item.getQuantity();
            this.notes = item.getNotes();
            this.options = item.getOptions().stream()
                    .map(KdsOrderOptionDto::fromEntity)
                    .collect(Collectors.toList());
        }

        public static KdsOrderItemDto fromEntity(OrderItem item) {
            return new KdsOrderItemDto(item);
        }

        // Getters
        public Long getOrderItemId() { return orderItemId; }
        public String getProductName() { return productName; }
        public Integer getQuantity() { return quantity; }
        public String getNotes() { return notes; }
        public List<KdsOrderOptionDto> getOptions() { return options; }
    }

    // 內部靜態類別 (Inner Static Class) - KdsOrderOptionDto
    public static class KdsOrderOptionDto {
        private final String optionName;
        // 注意：廚房不需要知道價格調整 (priceAdjustment)

        public KdsOrderOptionDto(ProductOption option) {
            this.optionName = option.getOptionName();
        }

        public static KdsOrderOptionDto fromEntity(ProductOption option) {
            return new KdsOrderOptionDto(option);
        }

        // Getters
        public String getOptionName() { return optionName; }
    }


    // KdsOrderDto 的建構子
    public KdsOrderDto(Order order) {
        this.orderId = order.getOrderId();
        this.orderNumber = order.getOrderNumber();
        this.status = order.getStatus();
        this.orderTime = order.getOrderTime();
        this.customerNote = order.getCustomerNote();
        this.items = order.getItems().stream()
                .map(KdsOrderItemDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 靜態工廠方法 (Static Factory Method)
     * @param order 完整的 Order 實體
     * @return 為 KDS 簡化的 KdsOrderDto
     */
    public static KdsOrderDto fromEntity(Order order) {
        return new KdsOrderDto(order);
    }

    // Getters
    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public OrderStatus getStatus() { return status; }
    public Date getOrderTime() { return orderTime; }
    public String getCustomerNote() { return customerNote; }
    public List<KdsOrderItemDto> getItems() { return items; }
}