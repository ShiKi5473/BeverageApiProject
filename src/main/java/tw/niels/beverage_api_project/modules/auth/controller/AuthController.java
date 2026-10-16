package tw.niels.beverage_api_project.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.ErrorResponse;
import tw.niels.beverage_api_project.common.service.RateLimitService;
import tw.niels.beverage_api_project.modules.auth.dto.GuestLoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.auth.dto.LoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.service.AuthService;
import tw.niels.beverage_api_project.modules.auth.service.SseTicketService;
import tw.niels.beverage_api_project.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.AUTH) // 使用定義好的常數
@Tag(name = "Authentication", description = "使用者認證與登入 API") // Swagger 分類
public class AuthController {

    // 【新增】速率限制參數：登入以「IP + 品牌 + 帳號」計數，防止暴力破解單一帳號密碼
    private static final int LOGIN_LIMIT = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);
    // 訪客登入以 IP 計數；同店 Wi-Fi 的顧客會共用公網 IP，因此上限放寬
    private static final int GUEST_LIMIT = 60;
    private static final Duration GUEST_WINDOW = Duration.ofMinutes(10);

    private final AuthService authService;
    private final SseTicketService sseTicketService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, SseTicketService sseTicketService,
                          RateLimitService rateLimitService) {
        this.authService = authService;
        this.sseTicketService = sseTicketService;
        this.rateLimitService = rateLimitService;
    }

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class); // 新增

    /**
     * 處理使用者登入請求 (員工與會員共用)。
     *
     * @param loginRequestDto 包含手機、密碼和品牌ID的請求物件。
     * @return 成功時回傳包含 JWT Token 的 ResponseEntity，失敗時回傳 401 Unauthorized（ErrorResponse 格式）。
     */
    @PostMapping("/login")
    @Operation(summary = "使用者登入", description = "員工或會員使用手機號碼與密碼登入，成功後回傳 JWT Token") // API 描述
    public ResponseEntity<?> authenticateUser(@RequestBody @Valid LoginRequestDto loginRequestDto,
                                              HttpServletRequest request) {
        // 【新增】速率限制（超過上限由 RateLimitService 拋出 429，GlobalExceptionHandler 轉為 ErrorResponse）
        rateLimitService.checkOrThrow(
                "login:" + clientIp(request) + ":" + loginRequestDto.getBrandId() + ":" + loginRequestDto.getUsername(),
                LOGIN_LIMIT, LOGIN_WINDOW);
        try {
            // 直接呼叫 AuthService，它會回傳完整的 DTO
            JwtAuthResponseDto jwtAuthResponseDto = authService.login(loginRequestDto);
            return ResponseEntity.ok(jwtAuthResponseDto);
        } catch (AuthenticationException e) {
            logger.warn("登入失敗 (品牌: {}): {}", loginRequestDto.getBrandId(), e.getMessage());
            // 【修改】錯誤回應改用統一的 ErrorResponse 格式
            ErrorResponse body = ErrorResponse.builder()
                    .error("LOGIN_FAILED")
                    .message("登入失敗：帳號、密碼或品牌不正確。")
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
    }

    @PostMapping("/guest")
    @Operation(summary = "訪客登入", description = "訪客輸入暱稱與品牌 ID，獲取臨時 JWT Token 以進行線上點餐")
    public ResponseEntity<JwtAuthResponseDto> guestLogin(@RequestBody @Valid GuestLoginRequestDto requestDto,
                                                         HttpServletRequest request) {
        // 【新增】依 IP 速率限制，避免大量簽發訪客 token
        rateLimitService.checkOrThrow("guest-login:" + clientIp(request), GUEST_LIMIT, GUEST_WINDOW);
        JwtAuthResponseDto jwtAuthResponseDto = authService.guestLogin(requestDto);
        return ResponseEntity.ok(jwtAuthResponseDto);
    }

    @PostMapping("/sse-ticket")
    @Operation(summary = "取得 SSE 連線票券", description = "產生一次性短期票券，用於建立 SSE 連線。票券有效期 30 秒，僅能使用一次。限品牌員工／會員。")
    public ResponseEntity<Map<String, String>> createSseTicket() {
        // 【修改】ticket 內存放完整身分（userId、brandId、storeId、role），因此只允許員工／會員（AppUserDetails）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            throw new AccessDeniedException("僅品牌員工或會員可建立 SSE 連線");
        }
        String ticket = sseTicketService.createTicket(user);
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }

    /**
     * 取得用戶端 IP。
     * 使用 getRemoteAddr()，不直接信任 X-Forwarded-For（可被偽造來繞過限流）；
     * 部署在反向代理後方時，應設定 server.forward-headers-strategy 讓容器改寫 remoteAddr。
     */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
