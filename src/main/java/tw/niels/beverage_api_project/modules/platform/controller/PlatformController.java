package tw.niels.beverage_api_project.modules.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.brand.dto.BrandResponseDto;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;
import tw.niels.beverage_api_project.modules.platform.dto.PlatformLoginRequestDto;
import tw.niels.beverage_api_project.modules.platform.service.PlatformAuthService;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserResponseDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/platform")
@Tag(name = "Platform Admin", description = "平台超級管理員專用 API")
public class PlatformController {

    private final PlatformAuthService platformAuthService;
    private final BrandService brandService;
    private final UserService userService;

    public PlatformController(PlatformAuthService platformAuthService, BrandService brandService, UserService userService) {
        this.platformAuthService = platformAuthService;
        this.brandService = brandService;
        this.userService = userService;
    }

    /**
     * 平台管理員登入 API
     */
    @PostMapping("/auth/login")
    @Operation(summary = "平台管理員登入", description = "取得平台管理權限的 JWT Token")
    public ResponseEntity<JwtAuthResponseDto> authenticatePlatformAdmin(@Valid @RequestBody PlatformLoginRequestDto loginRequestDto) {
        // 這裡會呼叫 PlatformAuthService，進而使用 PlatformAdminDetailsService
        JwtAuthResponseDto jwt = platformAuthService.login(loginRequestDto);
        return ResponseEntity.ok(jwt);
    }

    /**
     * 建立一個新品牌 (租戶)
     */
    @PostMapping("/brands")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "建立新品牌 (租戶)", description = "新增一個合作品牌 (僅平台管理員)")
    public ResponseEntity<BrandResponseDto> createBrand(@Valid @RequestBody CreateBrandRequestDto requestDto) {
        // 若有名稱衝突，Service 會拋出 IllegalStateException，由全域處理器轉為 409 Conflict
        Brand newBrand = brandService.createBrand(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrandResponseDto.fromEntity(newBrand));
    }

    /**
     * 為指定品牌建立第一位「品牌管理員 (BRAND_ADMIN)」
     */
    @PostMapping("/brands/{brandId}/admin-user")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "建立品牌初始管理員", description = "為新品牌建立第一個 BRAND_ADMIN 帳號 (僅平台管理員)")
    public ResponseEntity<CreateUserResponseDto> createBrandAdmin(
            @PathVariable Long brandId,
            @Valid @RequestBody CreateUserRequestDto createUserRequestDto) {

        createUserRequestDto.setBrandId(brandId);

        if (createUserRequestDto.getStaffProfile() == null ||
                createUserRequestDto.getStaffProfile().getRole() != tw.niels.beverage_api_project.modules.user.enums.StaffRole.BRAND_ADMIN) {
            // 主動拋出 BadRequestException 讓全域處理器處理
            throw new BadRequestException("Only BRAND_ADMIN can be created via this endpoint.");
        }

        try {
            User newUser = userService.createUser(createUserRequestDto);
            String msg = "Brand Admin created successfully for brand " + brandId + " with User ID: " + newUser.getUserId();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateUserResponseDto(msg, newUser.getUserId()));

        } catch (RuntimeException e) {
            // 將原本的 RuntimeException 包裝成明確的 BadRequestException 拋出
            // 這樣 GlobalExceptionHandler 就會回傳 400 Bad Request
            throw new BadRequestException(e.getMessage());
        }
    }
}
