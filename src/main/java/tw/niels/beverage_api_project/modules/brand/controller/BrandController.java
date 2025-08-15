package tw.niels.beverage_api_project.modules.brand.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

@RestController
@RequestMapping(ApiPaths.BRANDS) // 建議為 Brand 建立一個新的 API 路徑
public class BrandController {

    @Autowired
    private BrandService brandService;

    // 建立新品牌 (假設任何已認證使用者都能建立，真實世界中可能需要更嚴格的權限)
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createBrand(@Valid @RequestBody CreateBrandRequestDto requestDto) {
        try {
            Brand newBrand = brandService.createBrand(requestDto);
            return new ResponseEntity<>(newBrand, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
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
}