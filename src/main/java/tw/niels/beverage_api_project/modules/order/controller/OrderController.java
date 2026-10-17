package tw.niels.beverage_api_project.modules.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import tw.niels.beverage_api_project.modules.order.application.query.PreviewOrderQuery;
import tw.niels.beverage_api_project.modules.order.dto.*;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.GuestPrincipal;
import tw.niels.beverage_api_project.security.TenantPrincipal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 訂單處理 API（點餐、結帳、狀態更新、金額試算）
 *
 * 修改說明：
 * - 回應一律由 OrderQueryHandler 組出 OrderResponseDto（CQRS 讀取端直接讀 OrderEntity），不再回傳 Domain / Entity；
 * - GET /orders 新增 from / to（ISO 日期時間，皆可省略，預設最近 24 小時）與筆數上限，回應仍為 List；
 * - 新增 POST /orders/preview 金額試算（員工、會員、訪客皆可用，不寫入資料庫）；
 * - GET /orders/{id} 新增分店存取檢查（綁店員工只能看自己分店的訂單）；
 * - M-1 分店資料隔離：PATCH /{id}/status、PUT /{id}、PATCH /{id}/checkout、PATCH /{id}/accept
 *   原本只依品牌載入訂單，綁店員工可操作同品牌其他分店的訂單。現在由 Controller 把呼叫者的
 *   StoreScope（綁店員工 = 自己的分店；未綁店的品牌管理員 = 全品牌）放進 Command，
 *   由 Handler 在交易內載入訂單後統一檢查（不符 → 403），Controller 不做業務判斷；
 * - TESTER（測試人員）比照 STAFF 權限（使用者決策）：所有允許 STAFF 的 @PreAuthorize 一併加入 TESTER；
 * - POST /pos-checkout 改為 @Idempotent(required = true)：牽涉金額與扣點，缺少 Idempotency-Key 回 400。
 */
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
    private final OrderPreviewHandler orderPreviewHandler;
    private final ControllerHelperService helperService;
    // 新增：營業時區（TimeConfig 提供），解析不含時區的 from / to 參數
    private final ZoneId businessZoneId;

    public OrderController(PlaceOrderHandler placeOrderHandler,
                           PosCheckoutHandler posCheckoutHandler,
                           UpdateOrderStatusHandler updateOrderStatusHandler,
                           UpdateHeldOrderHandler updateHeldOrderHandler,
                           ProcessPaymentHandler processPaymentHandler,
                           AcceptOrderHandler acceptOrderHandler,
                           OrderQueryHandler orderQueryHandler,
                           OrderPreviewHandler orderPreviewHandler,
                           ControllerHelperService helperService,
                           ZoneId businessZoneId) {
        this.placeOrderHandler = placeOrderHandler;
        this.posCheckoutHandler = posCheckoutHandler;
        this.updateOrderStatusHandler = updateOrderStatusHandler;
        this.updateHeldOrderHandler = updateHeldOrderHandler;
        this.processPaymentHandler = processPaymentHandler;
        this.acceptOrderHandler = acceptOrderHandler;
        this.orderQueryHandler = orderQueryHandler;
        this.orderPreviewHandler = orderPreviewHandler;
        this.helperService = helperService;
        this.businessZoneId = businessZoneId;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Idempotent
    @Operation(summary = "建立新訂單 (暫存或待付款)", description = "status 僅允許 PENDING 或 HELD")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId();
        Long userId = helperService.getCurrentUserId();

        if (storeId == null) throw new BadRequestException("此帳號未綁定店家");

        PlaceOrderCommand command = new PlaceOrderCommand(
            brandId, storeId, userId, request.getStatus(), request.getItems()
        );
        Long orderId = placeOrderHandler.handle(command);

        return new ResponseEntity<>(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)), HttpStatus.CREATED);
    }

    @PostMapping("/pos-checkout")
    // 修改：強制要求 Idempotency-Key（前端一律會帶），避免網路重送造成重複建單 / 重複扣點；缺少時回 400
    @Idempotent(required = true)
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "POS 直接結帳", description = "點數於同一交易內同步扣除；點數不足回 422 且不建立訂單")
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

        return new ResponseEntity<>(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)), HttpStatus.CREATED);
    }

    /**
     * 新增：訂單金額試算（不寫入資料庫）。
     * - 員工：綁店員工以 token 的分店為準；memberId / pointsToUse 由 body 指定
     * - 會員：memberId 固定為本人（忽略 body 的 memberId）
     * - 訪客：不可使用點數（忽略 memberId / pointsToUse）
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER', 'MEMBER', 'GUEST')")
    @Operation(summary = "訂單金額試算", description = "回傳原價、促銷折扣、點數折抵、實際使用點數與應付金額，不建立訂單")
    public ResponseEntity<OrderPreviewResponseDto> previewOrder(@Valid @RequestBody OrderPreviewRequestDto request) {
        TenantPrincipal principal = helperService.getCurrentTenantPrincipal();

        Long storeId = request.getStoreId();
        Long memberId = request.getMemberId();
        Long pointsToUse = request.getPointsToUse();

        if (principal instanceof GuestPrincipal) {
            // 訪客沒有點數
            memberId = null;
            pointsToUse = 0L;
        } else if (principal instanceof AppUserDetails user) {
            if (hasRole(user, "ROLE_MEMBER")) {
                // 會員只能試算自己的點數
                memberId = user.userId();
            } else if (user.storeId() != null) {
                // 綁店員工以 token 中的分店為準
                storeId = user.storeId();
            }
        }

        return ResponseEntity.ok(orderPreviewHandler.handle(
            new PreviewOrderQuery(principal.brandId(), storeId, request.getItems(), memberId, pointsToUse)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "查詢訂單列表", description = "依下單時間新到舊，預設最近 24 小時，最多 500 筆")
    public ResponseEntity<List<OrderResponseDto>> getOrders(
            @RequestParam Long storeId,
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "下單時間起（ISO 8601，例：2026-10-17T00:00:00+08:00；未帶時區以營業時區解析）")
            @RequestParam(required = false) String from,
            @Parameter(description = "下單時間迄（不含，ISO 8601）")
            @RequestParam(required = false) String to) {
        Long brandId = helperService.getCurrentBrandId();
        helperService.validateStoreAccess(storeId);

        GetOrdersQuery query = new GetOrdersQuery(brandId, storeId, status,
            parseDateTime(from, "from"), parseDateTime(to, "to"));
        return ResponseEntity.ok(orderQueryHandler.handle(query));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "查詢訂單詳情")
    public ResponseEntity<OrderResponseDto> getOrderDetails(@PathVariable Long orderId) {
        Long brandId = helperService.getCurrentBrandId();
        OrderResponseDto dto = orderQueryHandler.handle(new GetOrderQuery(brandId, orderId));
        // 新增：綁店員工只能查看自己分店的訂單（品牌管理員不受限）
        helperService.validateStoreAccess(dto.getStoreId());
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Audit(action = "UPDATE_ORDER_STATUS")
    @Operation(summary = "更新訂單狀態", description = "支援 HELD、PREPARING（接單）、READY_FOR_PICKUP、CLOSED、CANCELLED；其他狀態回 400")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusDto request) {
        Long brandId = helperService.getCurrentBrandId();
        // 修改（M-1）：帶入呼叫者的分店範圍，由 Handler 在交易內檢查訂單所屬分店（不符 → 403）
        updateOrderStatusHandler.handle(new UpdateOrderStatusCommand(brandId, orderId, request.getStatus(), currentStoreScope()));
        return ResponseEntity.ok(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)));
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Audit(action = "UPDATE_HELD_ORDER")
    @Operation(summary = "更新暫存訂單")
    public ResponseEntity<OrderResponseDto> updateHeldOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        // 修改（M-1）：帶入呼叫者的分店範圍，由 Handler 在交易內檢查訂單所屬分店（不符 → 403）
        updateHeldOrderHandler.handle(new UpdateHeldOrderCommand(
            brandId, orderId, request.getStatus(), request.getItems(), currentStoreScope()));
        return ResponseEntity.ok(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)));
    }

    @PatchMapping("/{orderId}/checkout")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "訂單結帳付款", description = "點數於同一交易內同步扣除；點數不足回 422")
    public ResponseEntity<OrderResponseDto> processOrderPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody ProcessPaymentRequestDto request) {
        Long brandId = helperService.getCurrentBrandId();
        // 修改（M-1）：帶入呼叫者的分店範圍，由 Handler 在交易內檢查訂單所屬分店（不符 → 403）
        processPaymentHandler.handle(new ProcessPaymentCommand(
            brandId, orderId, request.getMemberId(), request.getPointsToUse(), request.getPaymentMethod(),
            currentStoreScope()
        ));
        return ResponseEntity.ok(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)));
    }

    @PatchMapping("/{orderId}/accept")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Audit(action = "ACCEPT_ORDER")
    @Operation(summary = "接受訂單", description = "線上訂單 AWAITING_ACCEPTANCE → PREPARING")
    public ResponseEntity<OrderResponseDto> acceptOrder(@PathVariable Long orderId) {
        Long brandId = helperService.getCurrentBrandId();
        // 修改（M-1）：帶入呼叫者的分店範圍，由 Handler 在交易內檢查訂單所屬分店（不符 → 403）
        acceptOrderHandler.handle(new AcceptOrderCommand(brandId, orderId, currentStoreScope()));
        return ResponseEntity.ok(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)));
    }

    // ─── 私有輔助方法（HTTP 參數轉換）─────────────────────────────────────

    /**
     * 新增（M-1）：由 token 取得呼叫者可操作的分店範圍。
     * 綁店員工（STAFF / MANAGER / TESTER）→ 自己的分店；未綁店的品牌管理員 → 全品牌；
     * 非管理員卻未綁店 → ControllerHelperService 直接拋 AccessDeniedException（403）。
     * 實際比對在 Handler 內進行（交易內、載入訂單之後），Controller 只負責轉換身分資訊。
     */
    private StoreScope currentStoreScope() {
        return StoreScope.ofNullable(helperService.getAccessibleStoreId());
    }

    /**
     * 解析 ISO 8601 日期時間參數（新增）：
     * 帶時區 / offset（如 2026-10-17T00:00:00Z、+08:00）直接轉 Instant；
     * 不帶時區（2026-10-17T00:00:00）或只有日期（2026-10-17）時以營業時區解析。
     *
     * @return 解析結果，參數為空時回傳 null
     * @throws BadRequestException 格式錯誤
     */
    private Instant parseDateTime(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeException ignored) {
            // 不是帶 offset 的格式，繼續嘗試其他格式
        }
        try {
            return LocalDateTime.parse(text).atZone(businessZoneId).toInstant();
        } catch (DateTimeException ignored) {
            // 不是本地日期時間，繼續嘗試只有日期
        }
        try {
            return LocalDate.parse(text).atStartOfDay(businessZoneId).toInstant();
        } catch (DateTimeException e) {
            throw new BadRequestException("參數 " + paramName + " 格式錯誤，請使用 ISO 8601 日期時間：" + value);
        }
    }

    /** 是否具有指定角色 */
    private static boolean hasRole(AppUserDetails user, String role) {
        return user.getAuthorities() != null && user.getAuthorities().stream()
            .anyMatch(a -> role.equals(a.getAuthority()));
    }
}
