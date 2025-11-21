package tw.niels.beverage_api_project.modules.brand.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
@Tag(name = "Brand Management", description = "品牌資訊與設定 API")
public class BrandController {

    private final BrandService brandService;
    private final ControllerHelperService helperService;

    public  BrandController(BrandService brandService,
                            ControllerHelperService helperService) {
        this.brandService = brandService;
        this.helperService = helperService;
    }



    // 取得所有品牌
    @GetMapping
    @Operation(summary = "取得所有品牌列表", description = "列出系統中所有已註冊的品牌")
    public ResponseEntity<List<Brand>> getAllBrands() {
        List<Brand> brands = brandService.getAllBrands();
        return ResponseEntity.ok(brands);
    }

    // 根據 ID 取得單一品牌
    @GetMapping("/{id}")
    @Operation(summary = "查詢單一品牌", description = "根據 Brand ID 取得詳細資訊")
    public ResponseEntity<Brand> getBrandById(@PathVariable Long id) {
        return brandService.getBrandById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新品牌的會員點數規則
     * 僅限該品牌的管理員 (BRAND_ADMIN) 操作
     */
    @PutMapping("/point-config")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "更新會員點數規則", description = "設定累積匯率與折抵匯率 (僅品牌管理員)")
    public ResponseEntity<BrandPointConfig> updatePointConfig(
            @Valid @RequestBody UpdatePointConfigDto requestDto) {

        // 取得當前登入者的 Brand ID (從 Token)
        Long currentBrandId = helperService.getCurrentBrandId();

        BrandPointConfig updatedConfig = brandService.updatePointConfig(currentBrandId, requestDto);

        return ResponseEntity.ok(updatedConfig);
    }
}