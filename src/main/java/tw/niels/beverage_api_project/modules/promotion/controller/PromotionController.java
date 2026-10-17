package tw.niels.beverage_api_project.modules.promotion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.promotion.dto.CreatePromotionRequestDto;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionResponseDto;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/brands/promotions")
// 修改：Tag 名稱原為批次改名殘留的 "PromotionEntity Management"
@Tag(name = "Promotion Management", description = "促銷活動管理 API")
public class PromotionController {

    private final PromotionService promotionService;
    private final ControllerHelperService helperService;

    public PromotionController(PromotionService promotionService, ControllerHelperService helperService) {
        this.promotionService = promotionService;
        this.helperService = helperService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "建立促銷活動", description = "設定折扣規則與適用商品")
    @Audit(action = "CREATE_PROMOTION")
    public ResponseEntity<PromotionResponseDto> createPromotion(
            @Valid @RequestBody CreatePromotionRequestDto requestDto) {

        Long brandId = helperService.getCurrentBrandId();
        // 修改：Service 直接回傳 DTO（在交易內轉換），Controller 不再接觸 JPA Entity
        PromotionResponseDto promotion = promotionService.createPromotion(brandId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(promotion);
    }

    @GetMapping
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(summary = "查詢促銷活動列表", description = "列出該品牌所有活動 (含已過期/停用)")
    public ResponseEntity<List<PromotionResponseDto>> getPromotions() {
        Long brandId = helperService.getCurrentBrandId();
        // 修改：Service 直接回傳 DTO（含適用商品，已在交易內載入）
        return ResponseEntity.ok(promotionService.getPromotionsByBrand(brandId));
    }

    @DeleteMapping("/{promotionId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "停用促銷活動", description = "將活動狀態設為無效 (軟刪除)")
    @Audit(action = "DEACTIVATE_PROMOTION")
    public ResponseEntity<Void> deactivatePromotion(@PathVariable Long promotionId) {
        Long brandId = helperService.getCurrentBrandId();
        promotionService.deactivatePromotion(brandId, promotionId);
        return ResponseEntity.noContent().build();
    }
}
