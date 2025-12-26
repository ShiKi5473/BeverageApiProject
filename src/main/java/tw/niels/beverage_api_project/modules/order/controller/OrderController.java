package tw.niels.beverage_api_project.modules.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.annotation.Idempotent;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.order.dto.*;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;
import tw.niels.beverage_api_project.modules.order.service.OrderService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.ORDERS)
@Tag(name = "Order Management", description = "訂單處理 API (點餐、結帳、狀態更新)")
public class OrderController {
    private final OrderService orderService;
    private final OrderProcessFacade orderProcessFacade; // 注入 Facade
    private final ControllerHelperService helperService;

    public OrderController(OrderService orderService,
                           OrderProcessFacade orderProcessFacade,
                           ControllerHelperService helperService) {
        this.orderService = orderService;
        this.orderProcessFacade = orderProcessFacade;
        this.helperService = helperService;
    }

    /**
     * 建立一筆新訂單
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Idempotent // 防止重複下單
    @Operation(summary = "建立新訂單 (暫存或待付款)", description = "通常用於將訂單存入 PENDING 或 HELD 狀態")
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequestDto createOrderRequestDto) {

        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId();
        Long userId = helperService.getCurrentUserId();

        if (storeId == null) {
            throw new BadRequestException("此帳號未綁定店家，無法建立訂單。");
        }

        // 改用 Facade 呼叫
        Order newOrder = orderProcessFacade.createOrder(brandId, storeId, userId, createOrderRequestDto);
        return new ResponseEntity<>(OrderResponseDto.fromEntity(newOrder), HttpStatus.CREATED);
    }

    /**
     * POS 直接結帳
     */
    @PostMapping("/pos-checkout")
    @Idempotent // 防止重複扣款
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "POS 直接結帳", description = "一步完成建立訂單、扣點數、付款，並將狀態設為 PREPARING")
    public ResponseEntity<OrderResponseDto> performPosCheckout(
            @Valid @RequestBody PosCheckoutRequestDto requestDto) {

        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId();
        Long userId = helperService.getCurrentUserId();

        if (storeId == null) {
            throw new BadRequestException("此帳號未綁定店家，無法建立訂單。");
        }

        // 改用 Facade 呼叫
        Order newOrder = orderProcessFacade.processPosCheckout(brandId, storeId, userId, requestDto);
        return new ResponseEntity<>(OrderResponseDto.fromEntity(newOrder), HttpStatus.CREATED);
    }

    /**
     * 預先計算訂單總金額
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "試算訂單金額", description = "僅計算總金額，不建立訂單")
    public ResponseEntity<OrderTotalDto> calculateOrderTotal(
            @Valid @RequestBody CreateOrderRequestDto createOrderRequestDto) {
        // 1. 取得當前品牌 ID
        Long brandId = helperService.getCurrentBrandId();

        // 2. 改用 Facade 呼叫，並傳入 brandId
        OrderTotalDto total = orderProcessFacade.calculateOrderTotal(brandId, createOrderRequestDto);

        return ResponseEntity.ok(total);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢訂單列表", description = "根據分店 ID 與訂單狀態篩選訂單")
    public ResponseEntity<List<OrderResponseDto>> getOrders(
            @RequestParam Long storeId,
            @RequestParam(required = false) OrderStatus status) {
        Long brandId = helperService.getCurrentBrandId();
        helperService.validateStoreAccess(storeId);

        List<Order> orders = orderService.getOrders(brandId, storeId, status);

        List<OrderResponseDto> dtos = orders.stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢訂單詳情", description = "取得單一訂單的完整資訊")
    public ResponseEntity<OrderResponseDto> getOrderDetails(@PathVariable Long orderId) {
        Long brandId = this.helperService.getCurrentBrandId();
        Order order = orderService.getOrderDetails(brandId, orderId);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(order));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "UPDATE_ORDER_STATUS")
    @Operation(summary = "更新訂單狀態", description = "手動更改訂單狀態 (如: 製作中 -> 可取餐)")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusDto requestDto) {
        Long brandId = this.helperService.getCurrentBrandId();
        Order updatedOrder = orderService.updateOrderStatus(brandId, orderId, requestDto.getStatus());
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "UPDATE_HELD_ORDER")
    @Operation(summary = "更新暫存訂單", description = "更新 HELD 狀態的訂單內容 (如: 顧客修改品項)")
    public ResponseEntity<OrderResponseDto> updateHeldOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateOrderRequestDto createOrderRequestDto) {
        Long brandId = helperService.getCurrentBrandId();
        OrderStatus newStatus = createOrderRequestDto.getStatus();
        if (newStatus != OrderStatus.HELD && newStatus != OrderStatus.PENDING) {
            throw new BadRequestException("更新訂單時，狀態只能設為 HELD 或 PENDING。");
        }
        Order updatedOrder = orderService.updateHeldOrder(brandId, orderId, createOrderRequestDto);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }

    @PatchMapping("/{orderId}/checkout")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "訂單結帳付款", description = "對已建立的訂單進行付款結帳動作")
    public ResponseEntity<OrderResponseDto> processOrderPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody ProcessPaymentRequestDto requestDto) {
        Long brandId = this.helperService.getCurrentBrandId();
        Order updatedOrder = orderService.processPayment(brandId, orderId, requestDto);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }

    @PatchMapping("/{orderId}/accept")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "ACCEPT_ORDER")
    @Operation(summary = "接受訂單", description = "將訂單從 AWAITING_ACCEPTANCE 轉為 PREPARING")
    public ResponseEntity<OrderResponseDto> acceptOrder(@PathVariable Long orderId) {
        Long brandId = this.helperService.getCurrentBrandId();
        Order updatedOrder = orderService.acceptOrder(brandId, orderId);
        return ResponseEntity.ok(OrderResponseDto.fromEntity(updatedOrder));
    }
}