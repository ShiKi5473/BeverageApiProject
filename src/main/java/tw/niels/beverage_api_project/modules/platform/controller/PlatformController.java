package tw.niels.beverage_api_project.modules.platform.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;
import tw.niels.beverage_api_project.modules.platform.dto.PlatformLoginRequestDto;
import tw.niels.beverage_api_project.modules.platform.service.PlatformAuthService;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/platform")
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
    public ResponseEntity<?> createBrand(@Valid @RequestBody CreateBrandRequestDto requestDto) {
        try {
            Brand newBrand = brandService.createBrand(requestDto);
            return new ResponseEntity<>(newBrand, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * 為指定品牌建立第一位「品牌管理員 (BRAND_ADMIN)」
     */
    @PostMapping("/brands/{brandId}/admin-user")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<String> createBrandAdmin(
            @PathVariable Long brandId,
            @Valid @RequestBody CreateUserRequestDto createUserRequestDto) {

        // 平台管理員在建立使用者時，必須手動指定 brandId
        createUserRequestDto.setBrandId(brandId);

        // 【安全檢查】確保建立的角色是 BRAND_ADMIN
        if (createUserRequestDto.getStaffProfile() == null ||
                createUserRequestDto.getStaffProfile().getRole() != tw.niels.beverage_api_project.modules.user.enums.StaffRole.BRAND_ADMIN) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only BRAND_ADMIN can be created via this endpoint.");
        }

        try {
            User newUser = userService.createUser(createUserRequestDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Brand Admin created successfully for brand " + brandId + " with User ID: " + newUser.getUserId());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}