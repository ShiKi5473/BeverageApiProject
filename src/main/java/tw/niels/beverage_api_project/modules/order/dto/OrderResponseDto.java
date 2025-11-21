package tw.niels.beverage_api_project.modules.order.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

@Data
@Schema(description = "訂單詳細資訊回應")
public class OrderResponseDto {

    @Schema(description = "訂單 ID", example = "1001")
    private Long orderId;

    @Schema(description = "訂單編號 (流水號)", example = "1-20231020-0001")
    private String orderNumber;

    @Schema(description = "分店 ID", example = "1")
    private Long storeId;

    @Schema(description = "會員 ID", example = "5")
    private Long memberId;

    @Schema(description = "經手員工 ID", example = "2")
    private Long staffId;

    @Schema(description = "訂單狀態", example = "COMPLETED")
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
        @Schema(description = "訂單品項 ID")
        private Long orderItemId;
        @Schema(description = "商品 ID")
        private Long productId;
        @Schema(description = "商品名稱")
        private String productName;
        @Schema(description = "數量")
        private Integer quantity;
        @Schema(description = "單價 (含選項加價)")
        private BigDecimal unitPrice;
        @Schema(description = "小計 (單價 x 數量)")
        private BigDecimal subtotal;
        @Schema(description = "單品備註")
        private String notes;
        @Schema(description = "已選選項列表")
        private List<OptionResponseDto> options;
    }

    @Data
    @Schema(description = "已選選項資訊")
    public static class OptionResponseDto {
        @Schema(description = "選項 ID")
        private Long optionId;
        @Schema(description = "選項名稱 (如: 半糖)")
        private String optionName;
        @Schema(description = "價格調整")
        private BigDecimal priceAdjustment;
    }

    @Data
    @Schema(description = "付款方式資訊")
    public static class PaymentMethodDto {
        @Schema(description = "付款方式 ID")
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

    public static OrderResponseDto fromEntity(Order order) {
        OrderResponseDto dto = new OrderResponseDto();
        dto.setOrderId(order.getOrderId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setStoreId(order.getStore().getStoreId());
        if (order.getMember() != null) dto.setMemberId(order.getMember().getUserId());
        if (order.getStaff() != null) dto.setStaffId(order.getStaff().getUserId());
        dto.setStatus(order.getStatus());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setFinalAmount(order.getFinalAmount());
        dto.setPointsUsed(order.getPointsUsed());
        dto.setPointsEarned(order.getPointsEarned());
        dto.setPaymentMethod(PaymentMethodDto.fromEntity(order.getPaymentMethod()));
        dto.setCustomerNote(order.getCustomerNote());
        dto.setOrderTime(order.getOrderTime());
        dto.setCompletedTime(order.getCompletedTime());

        List<OrderItemResponseDto> itemsDtos = order.getItems().stream().map(item -> {
            OrderItemResponseDto itemDto = new OrderItemResponseDto();
            itemDto.setOrderItemId(item.getOrderItemId());
            itemDto.setProductId(item.getProduct().getProductId());
            itemDto.setProductName(item.getProduct().getName());
            itemDto.setQuantity(item.getQuantity());
            itemDto.setUnitPrice(item.getUnitPrice());
            itemDto.setSubtotal(item.getSubtotal());
            itemDto.setNotes(item.getNotes());

            List<OptionResponseDto> optionDtos = item.getOptions().stream().map(option -> {
                OptionResponseDto optionDto = new OptionResponseDto();
                optionDto.setOptionId(option.getOptionId());
                optionDto.setOptionName(option.getOptionName());
                optionDto.setPriceAdjustment(option.getPriceAdjustment());
                return optionDto;
            }).collect(Collectors.toList());
            itemDto.setOptions(optionDtos);
            return itemDto;
        }).collect(Collectors.toList());

        dto.setItems(itemsDtos);
        return dto;
    }
}
