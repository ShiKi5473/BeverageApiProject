package tw.niels.beverage_api_project.modules.brand.controller;

import java.util.List;

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
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS) // 建議為 Brand 建立一個新的 API 路徑
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
    public ResponseEntity<List<Brand>> getAllBrands() {
        List<Brand> brands = brandService.getAllBrands();
        return ResponseEntity.ok(brands);
    }

    // 根據 ID 取得單一品牌
    @GetMapping("/{id}")
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
    public ResponseEntity<BrandPointConfig> updatePointConfig(
            @Valid @RequestBody UpdatePointConfigDto requestDto) {

        // 取得當前登入者的 Brand ID (從 Token)
        Long currentBrandId = helperService.getCurrentBrandId();

        BrandPointConfig updatedConfig = brandService.updatePointConfig(currentBrandId, requestDto);

        return ResponseEntity.ok(updatedConfig);
    }
}