package tw.niels.beverage_api_project.modules.kds.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 專門用於推送到 KDS (廚房顯示系統) 的訂單 DTO。
 * 僅包含 KDS 必要的資訊 (例如：品項、選項、備註)，
 * 移除了所有財務和會員相關的敏感資料。
 */
@Data // 自動生成 Getter, Setter, toString, equals, hashCode
@NoArgsConstructor // 生成無參數建構子 (Jackson 反序列化必備)
public class KdsOrderDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private Date orderTime;
    private String customerNote; // 訂單層級的備註
    private List<KdsOrderItemDto> items;

    // 保留自定義建構子 (供 Service 層轉換實體使用)
    public KdsOrderDto(OrderEntity OrderEntity) {
        this.orderId = OrderEntity.getId();
        this.orderNumber = OrderEntity.getOrderNumber();
        this.status = OrderEntity.getStatus();
        this.orderTime = OrderEntity.getOrderTime();
        this.customerNote = OrderEntity.getCustomerNote();
        this.items = OrderEntity.getItems().stream()
                .map(KdsOrderItemDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 靜態工廠方法 (Static Factory Method)
     */
    public static KdsOrderDto fromEntity(OrderEntity OrderEntity) {
        return new KdsOrderDto(OrderEntity);
    }

    // 內部靜態類別 (Inner Static Class) - KdsOrderItemDto
    @Data
    @NoArgsConstructor
    public static class KdsOrderItemDto {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long orderItemId;
        private String productName;
        private Integer quantity;
        private String notes; // 品項層級的備註 (例如：少冰)
        private List<KdsOrderOptionDto> options;

        public KdsOrderItemDto(OrderItemEntity item) {
            this.orderItemId = item.getId();
            this.productName = item.getProduct().getName();
            this.quantity = item.getQuantity();
            this.notes = item.getNotes();
            this.options = item.getOptions().stream()
                    .map(KdsOrderOptionDto::fromEntity)
                    .collect(Collectors.toList());
        }

        public static KdsOrderItemDto fromEntity(OrderItemEntity item) {
            return new KdsOrderItemDto(item);
        }
    }

    // 內部靜態類別 (Inner Static Class) - KdsOrderOptionDto
    @Data
    @NoArgsConstructor
    public static class KdsOrderOptionDto {
        private String optionName;
        // 注意：廚房不需要知道價格調整 (priceAdjustment)

        public KdsOrderOptionDto(ProductOptionEntity option) {
            this.optionName = option.getOptionName();
        }

        public static KdsOrderOptionDto fromEntity(ProductOptionEntity option) {
            return new KdsOrderOptionDto(option);
        }
    }
}