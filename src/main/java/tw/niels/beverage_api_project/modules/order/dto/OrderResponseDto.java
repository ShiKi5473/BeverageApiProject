package tw.niels.beverage_api_project.modules.order.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

public class OrderResponseDto {

    private Long orderId;
    private String orderNumber;
    private Long storeId;
    private Long memberId;
    private Long staffId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private Long pointsUsed;
    private Long pointsEarned;
    private PaymentMethodDto paymentMethod;
    private String customerNote;
    private Date orderTime;
    private Date completedTime;
    private List<OrderItemResponseDto> items;

    public PaymentMethodDto getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethodDto paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public static class OrderItemResponseDto {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private List<OptionResponseDto> options;

        // getter and setter for orderItemResponseDto
        public Long getOrderItemId() {
            return orderItemId;
        }

        public void setOrderItemId(Long orderItemId) {
            this.orderItemId = orderItemId;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public void setSubtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
        }

        public List<OptionResponseDto> getOptions() {
            return options;
        }

        public void setOptions(List<OptionResponseDto> options) {
            this.options = options;
        }

    }

    private static class OptionResponseDto {
        private Long optionId;
        private String optionName;
        private BigDecimal priceAdjustment;

        // getter and setter for optionResponseDto
        public Long getOptionId() {
            return optionId;
        }

        public void setOptionId(Long optionId) {
            this.optionId = optionId;
        }

        public String getOptionName() {
            return optionName;
        }

        public void setOptionName(String optionName) {
            this.optionName = optionName;
        }

        public BigDecimal getPriceAdjustment() {
            return priceAdjustment;
        }

        public void setPriceAdjustment(BigDecimal priceAdjustment) {
            this.priceAdjustment = priceAdjustment;
        }
    }

    public static class PaymentMethodDto {
        private Integer id;
        private String code;
        private String name;

        // Getters & Setters
        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public static PaymentMethodDto fromEntity(PaymentMethodEntity entity) {
            if (entity == null)
                return null;
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
        if (order.getMember() != null) {
            dto.setMemberId(order.getMember().getUserId());
        }
        if (order.getStaff() != null) {
            dto.setStaffId(order.getStaff().getUserId());
        }
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

    // getter and setter for orderResponseDto
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public Long getPointsUsed() {
        return pointsUsed;
    }

    public void setPointsUsed(Long pointsUsed) {
        this.pointsUsed = pointsUsed;
    }

    public Long getPointsEarned() {
        return pointsEarned;
    }

    public void setPointsEarned(Long pointsEarned) {
        this.pointsEarned = pointsEarned;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public Date getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(Date orderTime) {
        this.orderTime = orderTime;
    }

    public Date getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(Date completedTime) {
        this.completedTime = completedTime;
    }

    public List<OrderItemResponseDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponseDto> items) {
        this.items = items;
    }

}
