package tw.niels.beverage_api_project.modules.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderResponseDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderTotalDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.service.OrderService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.ORDERS)
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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

}
