package tw.niels.beverage_api_project.modules.promotion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.promotion.dto.CreatePromotionRequestDto;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionResponseDto;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/brands/promotions")
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
    public ResponseEntity<PromotionResponseDto> createPromotion(
            @Valid @RequestBody CreatePromotionRequestDto requestDto) {

        Long brandId = helperService.getCurrentBrandId();
        Promotion promotion = promotionService.createPromotion(brandId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PromotionResponseDto.fromEntity(promotion));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "查詢促銷活動列表", description = "列出該品牌所有活動 (含已過期/停用)")
    public ResponseEntity<List<PromotionResponseDto>> getPromotions() {
        Long brandId = helperService.getCurrentBrandId();
        List<Promotion> list = promotionService.getPromotionsByBrand(brandId);

        List<PromotionResponseDto> dtos = list.stream()
                .map(PromotionResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{promotionId}")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "停用促銷活動", description = "將活動狀態設為無效 (軟刪除)")
    public ResponseEntity<Void> deactivatePromotion(@PathVariable Long promotionId) {
        Long brandId = helperService.getCurrentBrandId();
        promotionService.deactivatePromotion(brandId, promotionId);
        return ResponseEntity.noContent().build();
    }
}