package tw.niels.beverage_api_project.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.auth.dto.GuestLoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.auth.dto.LoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.service.AuthService;
import tw.niels.beverage_api_project.modules.auth.service.SseTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.AUTH) // 使用定義好的常數
@Tag(name = "Authentication", description = "使用者認證與登入 API") // Swagger 分類
public class AuthController {

    private final AuthService authService;
    private final SseTicketService sseTicketService;

    public AuthController(AuthService authService, SseTicketService sseTicketService) {
        this.authService = authService;
        this.sseTicketService = sseTicketService;
    }

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class); // 新增

    /**
     * 處理使用者登入請求 (員工與會員共用)。
     * 
     * @param loginRequestDto 包含手機、密碼和品牌ID的請求物件。
     * @return 成功時回傳包含 JWT Token 的 ResponseEntity，失敗時回傳 401 Unauthorized。
     */
    @PostMapping("/login")
    @Operation(summary = "使用者登入", description = "員工或會員使用手機號碼與密碼登入，成功後回傳 JWT Token") // API 描述
    public ResponseEntity<?> authenticateUser(@RequestBody @Valid LoginRequestDto loginRequestDto) {
        try {
            logger.info("收到使用者登入請求: {}", loginRequestDto.getUsername());
            // 直接呼叫 AuthService，它會回傳完整的 DTO
            JwtAuthResponseDto jwtAuthResponseDto = authService.login(loginRequestDto);
            return ResponseEntity.ok(jwtAuthResponseDto);
        } catch (AuthenticationException e) {
            logger.warn("登入失敗 (帳號: {}): {}", loginRequestDto.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登入失敗：帳號、密碼或品牌不正確。");
        }
    }

    @PostMapping("/guest")
    @Operation(summary = "訪客登入", description = "訪客輸入暱稱，獲取臨時 JWT Token 以加入即時互動")
    public ResponseEntity<JwtAuthResponseDto> guestLogin(@RequestBody @Valid GuestLoginRequestDto requestDto) {
        // 使用 record 的存取方式： requestDto.displayName()
        logger.info("收到訪客登入請求: {}", requestDto.displayName());
        JwtAuthResponseDto jwtAuthResponseDto = authService.guestLogin(requestDto);
        return ResponseEntity.ok(jwtAuthResponseDto);
    }

    @PostMapping("/sse-ticket")
    @Operation(summary = "取得 SSE 連線票券", description = "產生一次性短期票券，用於建立 SSE 連線。票券有效期 30 秒，僅能使用一次。")
    public ResponseEntity<Map<String, String>> createSseTicket() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String ticket = sseTicketService.createTicket(username);
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }
}
