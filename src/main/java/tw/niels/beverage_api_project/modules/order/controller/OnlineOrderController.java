package tw.niels.beverage_api_project.modules.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.niels.beverage_api_project.common.annotation.Idempotent;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.common.service.RateLimitService;
import tw.niels.beverage_api_project.modules.order.application.command.SubmitOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.OrderQueryHandler;
import tw.niels.beverage_api_project.modules.order.application.handler.SubmitOnlineOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.query.GetOnlineOrderTicketQuery;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.order.dto.CreateOnlineOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OnlineOrderTicketResponseDto;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.GuestPrincipal;
import tw.niels.beverage_api_project.security.TenantPrincipal;

import java.time.Duration;

/**
 * 線上點餐 API（非同步處理）
 *
 * 修改說明（B-4、P0-9）：
 * - 開放 GUEST、MEMBER 與員工下單（原本只允許會員與員工，訪客 token 無法使用）；
 * - 請求改用 CreateOnlineOrderRequestDto：storeId 由 body 帶入、不接受 status
 *   （原本由前端決定初始狀態，可跳過接單；會員沒有 storeId 一律 400）；
 * - 下單者身分：訪客綁定 guestRef（"GUEST:uuid"），會員綁定 memberId，供訂單通知與點數使用
 *   （原本把員工欄位當成會員寫入，會員為 null）；
 * - 依 principalKey 做速率限制（Redis），並以 Idempotency-Key 防止重複送出；
 * - 新增 GET /{ticketId}：只限下單者本人查詢處理結果與訂單目前狀態；
 * - 業務邏輯（驗證分店、建立 ticket、送 MQ）移到 SubmitOnlineOrderHandler，Controller 只做 HTTP 轉換；
 * - POST 改為 @Idempotent(required = true)：缺少 Idempotency-Key 回 400（避免重複建單）。
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/online-orders")
@Tag(name = "Online Order", description = "線上點餐 API (非同步處理)")
public class OnlineOrderController {

    /** 速率限制：每位下單者每分鐘最多送出 10 筆線上訂單 */
    private static final int RATE_LIMIT_PER_WINDOW = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final SubmitOnlineOrderHandler submitOnlineOrderHandler;
    private final OrderQueryHandler orderQueryHandler;
    private final ControllerHelperService helperService;
    private final RateLimitService rateLimitService;

    public OnlineOrderController(SubmitOnlineOrderHandler submitOnlineOrderHandler,
                                 OrderQueryHandler orderQueryHandler,
                                 ControllerHelperService helperService,
                                 RateLimitService rateLimitService) {
        this.submitOnlineOrderHandler = submitOnlineOrderHandler;
        this.orderQueryHandler = orderQueryHandler;
        this.helperService = helperService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    // 修改：新增 GUEST（訪客線上點餐）與 TESTER
    @PreAuthorize("hasAnyRole('GUEST', 'MEMBER', 'STAFF', 'MANAGER', 'BRAND_ADMIN', 'TESTER')")
    // 冪等性檢查：相同 Idempotency-Key 重送時重放第一次的 202 回應
    // 修改：改為強制要求 Idempotency-Key（前端一律會帶），避免重送造成重複建單；缺少時回 400
    @Idempotent(required = true)
    @Operation(summary = "建立線上訂單 (非同步)", description = "將訂單請求放入佇列排隊，並立即回傳 Ticket ID 供後續查詢")
    public ResponseEntity<OnlineOrderTicketResponseDto> createOnlineOrder(
            @Valid @RequestBody CreateOnlineOrderRequestDto requestDto) {

        // 1. 取得下單者身分（員工、會員為 AppUserDetails；訪客為 GuestPrincipal）
        TenantPrincipal principal = helperService.getCurrentTenantPrincipal();
        String requester = principal.principalKey();

        // 2. 依下單者限制頻率（超過回 429）
        rateLimitService.checkOrThrow("online-order:" + requester, RATE_LIMIT_PER_WINDOW, RATE_LIMIT_WINDOW);

        // 3. 決定訂單綁定對象：訪客 → guestRef；會員 → memberId；員工代客下單兩者皆無
        Long memberId = null;
        String guestRef = null;
        if (principal instanceof GuestPrincipal guest) {
            guestRef = guest.principalKey();
        } else if (principal instanceof AppUserDetails user && isMember(user)) {
            memberId = user.userId();
        }

        String ticketId = submitOnlineOrderHandler.handle(new SubmitOnlineOrderCommand(
                principal.brandId(),
                requestDto.getStoreId(),
                requester,
                memberId,
                guestRef,
                requestDto.getItems(),
                requestDto.getCustomerNote()
        ));

        // 4. 立即回傳 202 Accepted
        return ResponseEntity.accepted()
                .body(OnlineOrderTicketResponseDto.queued(ticketId, OnlineOrderTicketService.MSG_QUEUED));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAnyRole('GUEST', 'MEMBER', 'STAFF', 'MANAGER', 'BRAND_ADMIN', 'TESTER')")
    @Operation(summary = "查詢線上訂單處理結果", description = "只有送出該 ticket 的使用者本人可以查詢；不存在或非本人皆回 404")
    public ResponseEntity<OnlineOrderTicketResponseDto> getOnlineOrderTicket(@PathVariable String ticketId) {
        TenantPrincipal principal = helperService.getCurrentTenantPrincipal();
        return ResponseEntity.ok(orderQueryHandler.handle(
                new GetOnlineOrderTicketQuery(principal.brandId(), principal.principalKey(), ticketId)));
    }

    /** 是否為會員帳號（ROLE_MEMBER） */
    private static boolean isMember(AppUserDetails user) {
        return user.getAuthorities() != null && user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
    }
}
