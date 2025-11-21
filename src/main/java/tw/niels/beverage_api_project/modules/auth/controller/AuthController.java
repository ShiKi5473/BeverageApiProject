package tw.niels.beverage_api_project.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.auth.dto.LoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.service.AuthService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.AUTH) // 使用定義好的常數
@Tag(name = "Authentication", description = "使用者認證與登入 API") // Swagger 分類
public class AuthController {

    private final AuthService authService;

    // 推薦使用建構子注入 (Constructor Injection)
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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
            System.out.println("收到登入請求");
            // 直接呼叫 AuthService，它會回傳完整的 DTO
            JwtAuthResponseDto jwtAuthResponseDto = authService.login(loginRequestDto);
            return ResponseEntity.ok(jwtAuthResponseDto);
        } catch (AuthenticationException e) {
            // 如果 Spring Security 在認證過程中拋出異常（例如密碼錯誤），則捕捉它
            System.out.println("failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登入失敗：帳號、密碼或品牌不正確。");
        }
    }
}
