package tw.niels.beverage_api_project.modules.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.order.dto.*;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.service.OrderService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.ORDERS)
public class OrderController {
    private final OrderService orderService;
    private final ControllerHelperService helperService;

    public OrderController(OrderService orderService, ControllerHelperService helperService) {
        this.orderService = orderService;
        this.helperService = helperService;
    }


    /**
     * 建立一筆新訂單
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequestDto createOrderRequestDto) {
        Order newOrder = orderService.createOrder(createOrderRequestDto);
        return new ResponseEntity<>(OrderResponseDto.fromEntity(newOrder), HttpStatus.CREATED);
    }

    /**
     * 預先計算訂單總金額
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<OrderTotalDto> calculateOrderTotal(
            @Valid @RequestBody CreateOrderRequestDto createOrderRequestDto) {
        OrderTotalDto total = orderService.calculateOrderTotal(createOrderRequestDto);
        return ResponseEntity.ok(total);
    }

    /**
     * 查詢訂單列表 (店家使用)
     * GET /api/v1/orders?storeId={storeId}&status={status}
     * status 參數是可選的 (PENDING, PREPARING, COMPLETED, CANCELLED)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<List<OrderResponseDto>> getOrders(
            @RequestParam Long storeId,
            @RequestParam(required = false) OrderStatus status) {

        // 從 JWT 取得 brandId 進行驗證與查詢

        Long brandId = helperService.getCurrentBrandId();

        // TODO: 在 Service 層或 Controller 層加入更嚴格的權限檢查：
        // 檢查 userDetails 中的 storeId (如果有的話) 是否與請求的 storeId 一致，或者角色是否為 BRAND_ADMIN
        // 例如: if (!userDetails.getRole().equals("BRAND_ADMIN") &&
        // !userDetails.getStoreId().equals(storeId)) { throw new
        // AccessDeniedException(...) }

        Optional<OrderStatus> statusOptional = Optional.ofNullable(status); // 將可能為 null 的 status 轉為 Optional

        List<Order> orders = orderService.getOrders(brandId, storeId, statusOptional);
        List<OrderResponseDto> dtos = orders.stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * 取得單一訂單詳情
     * GET /api/v1/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<OrderResponseDto> getOrderDetails(@PathVariable Long orderId) {

        Long brandId = this.helperService.getCurrentBrandId();

        Order order = orderService.getOrderDetails(brandId, orderId);

        // TODO: 可選的權限檢查：檢查目前使用者是否有權限查看此訂單 (可能基於 order.getStore().getStoreId())

        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    /**
     * 更新訂單狀態
     * PATCH /api/v1/orders/{orderId}/status
     */
    @PatchMapping("/{orderId}/status") // 使用 PATCH 通常更適合部分更新
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusDto requestDto) {

        Long brandId = this.helperService.getCurrentBrandId();

        // TODO: 可選的權限檢查：檢查目前使用者是否有權限修改此訂單 (可能基於訂單的 storeId)

        Order updatedOrder = orderService.updateOrderStatus(brandId, orderId, requestDto.getStatus());
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }

    /**
     * 處理一筆現有訂單的付款 (結帳)
     * PATCH /api/v1/orders/{orderId}/checkout
     */
    @PatchMapping("/{orderId}/checkout")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<OrderResponseDto> processOrderPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody ProcessPaymentRequestDto requestDto) {

        Long brandId = this.helperService.getCurrentBrandId();

        Order updatedOrder = orderService.processPayment(brandId, orderId, requestDto);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }

}
