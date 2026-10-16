package tw.niels.beverage_api_project.modules.kds.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 專門用於推送到 KDS (廚房顯示系統) 的訂單 DTO。
 * 僅包含 KDS 必要的資訊 (例如：品項、規格、選項、備註)，
 * 移除了所有財務和會員相關的敏感資料。
 * <p>
 * 【重要】{@link #fromEntity(OrderEntity)} 會讀取 JPA 延遲載入的集合（items、options、product、productVariant），
 * 必須在交易內呼叫（KdsService 以唯讀 TransactionTemplate 包住），不可在 @Async 執行緒中直接對游離實體呼叫。
 * 本 DTO 會經過 RabbitMQ（Jackson）序列化/反序列化，因此保留無參數建構子與 setter。
 * </p>
 */
@Data // 自動生成 Getter, Setter, toString, equals, hashCode
@NoArgsConstructor // 生成無參數建構子 (Jackson 反序列化必備)
public class KdsOrderDto {

    // TSID 超過 JS 安全整數，以字串輸出
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private String orderNumber;
    // 【修改】改為字串（狀態名稱），避免 DTO 依賴 JPA 層 enum
    private String status;
    private Date orderTime;
    private String customerNote; // 訂單層級的備註
    private List<KdsOrderItemDto> items;

    /**
     * 靜態工廠方法：由 JPA Entity 轉換（須在交易內呼叫）。
     */
    public static KdsOrderDto fromEntity(OrderEntity order) {
        KdsOrderDto dto = new KdsOrderDto();
        dto.orderId = order.getId();
        dto.orderNumber = order.getOrderNumber();
        dto.status = order.getStatus() != null ? order.getStatus().name() : null;
        dto.orderTime = order.getOrderTime();
        dto.customerNote = order.getCustomerNote();
        // items 為 HashSet，依 TSID（建立順序）排序，讓廚房畫面品項順序固定
        dto.items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItemEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(KdsOrderItemDto::fromEntity)
                .toList();
        return dto;
    }

    // 內部靜態類別 (Inner Static Class) - KdsOrderItemDto
    @Data
    @NoArgsConstructor
    public static class KdsOrderItemDto {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long orderItemId;
        private String productName;
        // 【新增】規格名稱（例如：大杯），廚房需要知道杯型
        private String variantName;
        private Integer quantity;
        private String notes; // 品項層級的備註 (例如：少冰)
        // 選項永不為 null（前端 kds.js 直接讀取 options.length）
        private List<KdsOrderOptionDto> options = List.of();

        public static KdsOrderItemDto fromEntity(OrderItemEntity item) {
            KdsOrderItemDto dto = new KdsOrderItemDto();
            dto.orderItemId = item.getId();
            ProductSnapshot snapshot = item.getProductSnapshot();
            // 優先使用下單當下的快照名稱（商品改名後廚房仍顯示下單時名稱），舊訂單沒有快照則讀取關聯
            dto.productName = (snapshot != null && snapshot.name() != null)
                    ? snapshot.name()
                    : (item.getProduct() != null ? item.getProduct().getName() : null);
            dto.variantName = (snapshot != null && snapshot.variantName() != null)
                    ? snapshot.variantName()
                    : (item.getProductVariant() != null ? item.getProductVariant().getName() : null);
            dto.quantity = item.getQuantity();
            dto.notes = item.getNotes();
            dto.options = item.getOptions() == null ? List.of() : item.getOptions().stream()
                    .sorted(Comparator.comparing(ProductOptionEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(KdsOrderOptionDto::fromEntity)
                    .toList();
            return dto;
        }
    }

    // 內部靜態類別 (Inner Static Class) - KdsOrderOptionDto
    @Data
    @NoArgsConstructor
    public static class KdsOrderOptionDto {
        private String optionName;
        // 注意：廚房不需要知道價格調整 (priceAdjustment)

        public static KdsOrderOptionDto fromEntity(ProductOptionEntity option) {
            KdsOrderOptionDto dto = new KdsOrderOptionDto();
            dto.optionName = option.getOptionName();
            return dto;
        }
    }
}
