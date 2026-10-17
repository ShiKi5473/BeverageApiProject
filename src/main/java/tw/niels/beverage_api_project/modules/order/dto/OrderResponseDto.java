package tw.niels.beverage_api_project.modules.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 訂單詳細資訊回應 DTO（CQRS 讀取端）。
 *
 * 修改說明：
 * - 改由 {@link #fromEntity(OrderEntity)} 直接從 JPA OrderEntity（已有 entity graph）組裝，
 *   讀取端不再經過 Domain Model（原本的 fromDomain 沒有填 options，KDS 會因 null 而崩潰）。
 * - 所有 ID 一律輸出為字串（ToStringSerializer），避免 TSID 超過 JS 安全整數範圍而失真。
 * - 品項新增 variantId / variantName，options 永遠不為 null（沒有選項時為空陣列）。
 * - paymentMethod 補齊 {id, code, name}。
 * - 舊訂單（V5 之前沒有商品快照）改以關聯的商品 / 規格名稱作為備援，仍可正常讀取。
 */
@Data
@Schema(description = "訂單詳細資訊回應")
public class OrderResponseDto {

    @Schema(description = "訂單 ID（字串）", example = "1001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @Schema(description = "訂單編號 (流水號)", example = "1-20231020-0001")
    private String orderNumber;

    @Schema(description = "分店 ID（字串）", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storeId;

    // 修改：會員 ID 改為字串輸出
    @Schema(description = "會員 ID（字串）", example = "5")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberId;

    // 修改：員工 ID 改為字串輸出
    @Schema(description = "經手員工 ID（字串）", example = "2")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    @Schema(description = "訂單狀態", example = "PREPARING")
    private OrderStatus status;

    @Schema(description = "總金額 (原價)", example = "150.00")
    private BigDecimal totalAmount;

    @Schema(description = "折扣金額", example = "10.00")
    private BigDecimal discountAmount;

    @Schema(description = "最終實收金額", example = "140.00")
    private BigDecimal finalAmount;

    @Schema(description = "使用點數", example = "100")
    private Long pointsUsed;

    @Schema(description = "獲得點數", example = "14")
    private Long pointsEarned;

    @Schema(description = "付款方式")
    private PaymentMethodDto paymentMethod;

    @Schema(description = "顧客備註", example = "飲料放管理室")
    private String customerNote;

    @Schema(description = "下單時間")
    private Date orderTime;

    @Schema(description = "完成時間")
    private Date completedTime;

    @Schema(description = "訂單品項列表")
    private List<OrderItemResponseDto> items;

    @Data
    @Schema(description = "訂單內單一品項資訊")
    public static class OrderItemResponseDto {
        @Schema(description = "訂單品項 ID（字串）", example = "501")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long orderItemId;

        @Schema(description = "商品 ID（字串）", example = "101")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long productId;

        @Schema(description = "商品名稱", example = "珍珠奶茶")
        private String productName;

        // 新增：規格 ID / 名稱（前端與 KDS 需要顯示「大杯」等規格）
        @Schema(description = "規格 ID（字串）", example = "102")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long variantId;

        @Schema(description = "規格名稱", example = "大杯")
        private String variantName;

        @Schema(description = "數量", example = "2")
        private Integer quantity;

        @Schema(description = "單價 (含選項加價)", example = "55.00")
        private BigDecimal unitPrice;

        @Schema(description = "小計 (單價 x 數量)", example = "110.00")
        private BigDecimal subtotal;

        @Schema(description = "單品備註", example = "少冰")
        private String notes;

        // 修改：永遠不為 null（沒有選項時為空陣列）
        @Schema(description = "已選選項列表（永不為 null）")
        private List<OptionResponseDto> options = List.of();
    }

    @Data
    @Schema(description = "已選選項資訊")
    public static class OptionResponseDto {
        @Schema(description = "選項 ID（字串）", example = "20")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long optionId;

        @Schema(description = "選項名稱", example = "半糖")
        private String optionName;

        @Schema(description = "價格調整", example = "0.00")
        private BigDecimal priceAdjustment;
    }

    @Data
    @Schema(description = "付款方式資訊")
    public static class PaymentMethodDto {
        // 修改：付款方式 ID 也依「所有 ID 皆為字串」的規則輸出
        @Schema(description = "付款方式 ID（字串）", example = "1")
        @JsonSerialize(using = ToStringSerializer.class)
        private Integer id;

        @Schema(description = "代碼", example = "CASH")
        private String code;

        @Schema(description = "名稱", example = "現金")
        private String name;

        public static PaymentMethodDto fromEntity(PaymentMethodEntity entity) {
            if (entity == null) return null;
            PaymentMethodDto dto = new PaymentMethodDto();
            dto.setId(entity.getPaymentMethodId());
            dto.setCode(entity.getCode());
            dto.setName(entity.getName());
            return dto;
        }
    }

    /**
     * 由 JPA OrderEntity 組裝回應（讀取端，需在交易內呼叫）。
     *
     * 新增方法（取代 fromDomain）：OrderJpaRepository 的查詢已用 entity graph 一次載入
     * 品項、商品、規格、選項與付款方式，因此這裡存取關聯不會產生 N+1 查詢；
     * 品牌 / 店家 / 會員 / 員工只讀 ID（Hibernate 代理取 ID 不會觸發載入）。
     *
     * @param entity 訂單實體
     * @return 訂單回應 DTO，entity 為 null 時回傳 null
     */
    public static OrderResponseDto fromEntity(OrderEntity entity) {
        if (entity == null) return null;

        OrderResponseDto dto = new OrderResponseDto();
        dto.setOrderId(entity.getId());
        dto.setOrderNumber(entity.getOrderNumber());
        dto.setStoreId(entity.getStore() != null ? entity.getStore().getId() : null);
        dto.setMemberId(entity.getMember() != null ? entity.getMember().getId() : null);
        dto.setStaffId(entity.getStaff() != null ? entity.getStaff().getId() : null);
        dto.setStatus(entity.getStatus());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setFinalAmount(entity.getFinalAmount());
        dto.setPointsUsed(entity.getPointsUsed());
        dto.setPointsEarned(entity.getPointsEarned());
        // 修改：付款方式展開為 {id, code, name}（原本只有 id）
        dto.setPaymentMethod(PaymentMethodDto.fromEntity(entity.getPaymentMethod()));
        dto.setCustomerNote(entity.getCustomerNote());
        dto.setOrderTime(entity.getOrderTime());
        dto.setCompletedTime(entity.getCompletedTime());

        // 品項依 ID（TSID 依時間遞增）排序，讓輸出順序穩定（items 是 HashSet）
        List<OrderItemResponseDto> itemDtos = entity.getItems() == null ? List.of()
                : entity.getItems().stream()
                    .sorted(Comparator.comparing(OrderItemEntity::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(OrderResponseDto::toItemDto)
                    .toList();
        dto.setItems(itemDtos);
        return dto;
    }

    /**
     * 品項轉換：優先使用下單當下的商品快照；舊訂單沒有快照時退回關聯的商品 / 規格資料。
     */
    private static OrderItemResponseDto toItemDto(OrderItemEntity item) {
        OrderItemResponseDto itemDto = new OrderItemResponseDto();
        ProductSnapshot snapshot = item.getProductSnapshot();

        itemDto.setOrderItemId(item.getId());
        itemDto.setProductId(item.getProduct() != null ? item.getProduct().getId()
                : (snapshot != null ? snapshot.productId() : null));
        // 商品名稱：快照 → 商品關聯（舊資料備援）
        String productName = snapshot != null ? snapshot.name() : null;
        if (productName == null && item.getProduct() != null) {
            productName = item.getProduct().getName();
        }
        itemDto.setProductName(productName);

        // 規格：ID 取關聯；名稱取快照 → 規格關聯（舊資料備援）
        itemDto.setVariantId(item.getProductVariant() != null ? item.getProductVariant().getId() : null);
        String variantName = snapshot != null ? snapshot.variantName() : null;
        if (variantName == null && item.getProductVariant() != null) {
            variantName = item.getProductVariant().getName();
        }
        itemDto.setVariantName(variantName);

        itemDto.setQuantity(item.getQuantity());
        itemDto.setUnitPrice(item.getUnitPrice());
        itemDto.setSubtotal(item.getSubtotal());
        itemDto.setNotes(item.getNotes());

        // 選項：填入名稱與加價，永不為 null
        List<OptionResponseDto> options = item.getOptions() == null ? List.of()
                : item.getOptions().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ProductOptionEntity::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(OrderResponseDto::toOptionDto)
                    .toList();
        itemDto.setOptions(options);
        return itemDto;
    }

    private static OptionResponseDto toOptionDto(ProductOptionEntity option) {
        OptionResponseDto optionDto = new OptionResponseDto();
        optionDto.setOptionId(option.getId());
        optionDto.setOptionName(option.getOptionName());
        optionDto.setPriceAdjustment(option.getPriceAdjustment());
        return optionDto;
    }
}
