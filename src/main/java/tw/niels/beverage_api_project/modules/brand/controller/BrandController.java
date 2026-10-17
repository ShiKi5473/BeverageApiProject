package tw.niels.beverage_api_project.modules.brand.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.brand.application.command.UpdatePointConfigCommand;
import tw.niels.beverage_api_project.modules.brand.application.handler.BrandCommandHandler;
import tw.niels.beverage_api_project.modules.brand.application.handler.BrandQueryHandler;
import tw.niels.beverage_api_project.modules.brand.application.query.GetAllBrandsQuery;
import tw.niels.beverage_api_project.modules.brand.application.query.GetBrandByIdQuery;
import tw.niels.beverage_api_project.modules.brand.dto.BrandResponseDto;
import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;
import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
// 清理(P3)：批次改名殘留 "BrandEntity Management" → "Brand Management"
@Tag(name = "Brand Management", description = "品牌資訊與設定 API")
public class BrandController {

    private final BrandCommandHandler commandHandler;
    private final BrandQueryHandler queryHandler;
    private final ControllerHelperService helperService;

    public BrandController(BrandCommandHandler commandHandler,
                           BrandQueryHandler queryHandler,
                           ControllerHelperService helperService) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.helperService = helperService;
    }

    @GetMapping
    @Operation(summary = "取得所有品牌列表 (僅限平台管理員)")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<BrandResponseDto>> getAllBrands() {
        return ResponseEntity.ok(queryHandler.handle(new GetAllBrandsQuery()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查詢單一品牌", description = "平台管理員可查任一品牌；租戶使用者僅能查自己的品牌")
    public ResponseEntity<BrandResponseDto> getBrandById(@PathVariable Long id) {
        // 修正 IDOR(S-1)：原本任何已登入者都能讀取任一品牌。
        // 平台管理員放行；其餘使用者（員工／會員）只能讀取自己所屬的品牌。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isPlatformAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> AuthorityConstants.ROLE_PLATFORM_ADMIN.equals(a.getAuthority()));
        if (!isPlatformAdmin && !id.equals(helperService.getCurrentBrandId())) {
            // 改拋 AccessDeniedException：由 GlobalExceptionHandler 轉成 403 + 統一的 ErrorResponse 格式
            // （原本回傳空 body 的 403，前端讀不到錯誤訊息）
            throw new AccessDeniedException("無權限查詢其他品牌的資料");
        }

        return queryHandler.handle(new GetBrandByIdQuery(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新品牌的會員點數規則
     * 僅限該品牌的管理員 (BRAND_ADMIN) 操作
     */
    @PutMapping("/point-config")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "更新會員點數規則")
    @Audit(action = "UPDATE_POINT_CONFIG")
    public ResponseEntity<Void> updatePointConfig(
            @Valid @RequestBody UpdatePointConfigDto requestDto) {
        Long currentBrandId = helperService.getCurrentBrandId();
        commandHandler.handle(new UpdatePointConfigCommand(currentBrandId, requestDto));
        return ResponseEntity.noContent().build();
    }
}