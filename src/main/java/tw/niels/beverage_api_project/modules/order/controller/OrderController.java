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
import tw.niels.beverage_api_project.modules.order.application.command.*;
import tw.niels.beverage_api_project.modules.order.application.handler.*;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrderQuery;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrdersQuery;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.dto.*;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.ORDERS)
@Tag(name = "Order Management", description = "訂單處理 API (點餐、結帳、狀態更新)")
public class OrderController {

    private final PlaceOrderHandler placeOrderHandler;
    private final PosCheckoutHandler posCheckoutHandler;
    private final UpdateOrderStatusHandler updateOrderStatusHandler;
    private final UpdateHeldOrderHandler updateHeldOrderHandler;
    private final ProcessPaymentHandler processPaymentHandler;
    private final AcceptOrderHandler acceptOrderHandler;
    private final OrderQueryHandler orderQueryHandler;
    private final ControllerHelperService helperService;

    public OrderController(PlaceOrderHandler placeOrderHandler,
                           PosCheckoutHandler posCheckoutHandler,
                           UpdateOrderStatusHandler updateOrderStatusHandler,
                           UpdateHeldOrderHandler updateHeldOrderHandler,
                           ProcessPaymentHandler processPaymentHandler,
                           AcceptOrderHandler acceptOrderHandler,
                           OrderQueryHandler orderQueryHandler,
                           ControllerHelperService helperService) {
        this.placeOrderHandler = placeOrderHandler;
        this.posCheckoutHandler = posCheckoutHandler;
        this.updateOrderStatusHandler = updateOrderStatusHandler;
        this.updateHeldOrderHandler = updateHeldOrderHandler;
        this.processPaymentHandler = processPaymentHandler;
        this.acceptOrderHandler = acceptOrderHandler;
        this.orderQueryHandler = orderQueryHandler;
        this.helperService = helperService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Idempotent
    @Operation(summary = "建立新訂單 (暫存或待付款)")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId();
        Long userId = helperService.getCurrentUserId();

        if (storeId == null) throw new BadRequestException("此帳號未綁定店家");

        PlaceOrderCommand command = new PlaceOrderCommand(
            brandId, storeId, userId, request.getStatus(), request.getItems()
        );
        Long orderId = placeOrderHandler.handle(command);
        
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return new ResponseEntity<>(OrderResponseDto.fromDomain(order), HttpStatus.CREATED);
    }

    @PostMapping("/pos-checkout")
    @Idempotent
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "POS 直接結帳")
    public ResponseEntity<OrderResponseDto> performPosCheckout(@Valid @RequestBody PosCheckoutRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId();
        Long userId = helperService.getCurrentUserId();

        if (storeId == null) throw new BadRequestException("此帳號未綁定店家");

        PosCheckoutCommand command = new PosCheckoutCommand(
            brandId, storeId, userId, request.getItems(), request.getMemberId(),
            request.getPointsToUse(), request.getPaymentMethod()
        );
        Long orderId = posCheckoutHandler.handle(command);

        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return new ResponseEntity<>(OrderResponseDto.fromDomain(order), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢訂單列表")
    public ResponseEntity<List<OrderResponseDto>> getOrders(
            @RequestParam Long storeId,
            @RequestParam(required = false) OrderStatus status) {
        Long brandId = helperService.getCurrentBrandId();
        helperService.validateStoreAccess(storeId);

        List<Order> orders = orderQueryHandler.handle(new GetOrdersQuery(brandId, storeId, status));
        List<OrderResponseDto> dtos = orders.stream()
                .map(OrderResponseDto::fromDomain)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢訂單詳情")
    public ResponseEntity<OrderResponseDto> getOrderDetails(@PathVariable Long orderId) {
        Long brandId = helperService.getCurrentBrandId();
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return ResponseEntity.ok(OrderResponseDto.fromDomain(order));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "UPDATE_ORDER_STATUS")
    @Operation(summary = "更新訂單狀態")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusDto request) {
        Long brandId = helperService.getCurrentBrandId();
        updateOrderStatusHandler.handle(new UpdateOrderStatusCommand(brandId, orderId, request.getStatus()));
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return ResponseEntity.ok(OrderResponseDto.fromDomain(order));
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "UPDATE_HELD_ORDER")
    @Operation(summary = "更新暫存訂單")
    public ResponseEntity<OrderResponseDto> updateHeldOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateOrderRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        updateHeldOrderHandler.handle(new UpdateHeldOrderCommand(brandId, orderId, request.getStatus(), request.getItems()));
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return ResponseEntity.ok(OrderResponseDto.fromDomain(order));
    }

    @PatchMapping("/{orderId}/checkout")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "訂單結帳付款")
    public ResponseEntity<OrderResponseDto> processOrderPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody ProcessPaymentRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        processPaymentHandler.handle(new ProcessPaymentCommand(
            brandId, orderId, request.getMemberId(), request.getPointsToUse(), request.getPaymentMethod()
        ));
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return ResponseEntity.ok(OrderResponseDto.fromDomain(order));
    }

    @PatchMapping("/{orderId}/accept")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Audit(action = "ACCEPT_ORDER")
    @Operation(summary = "接受訂單")
    public ResponseEntity<OrderResponseDto> acceptOrder(@PathVariable Long orderId) {
        Long brandId = helperService.getCurrentBrandId();
        acceptOrderHandler.handle(new AcceptOrderCommand(brandId, orderId));
        Order order = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        return ResponseEntity.ok(OrderResponseDto.fromDomain(order));
    }
}