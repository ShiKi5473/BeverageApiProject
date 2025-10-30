package tw.niels.beverage_api_project.modules.product.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.product.dto.CategoryResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateCategoryRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.service.CategoryService;
import tw.niels.beverage_api_project.modules.product.service.ProductService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
public class ProductController {
    private final ProductService productService;
    private final CategoryService categoryService;
    private final ControllerHelperService helperService;

    public ProductController(ProductService productService, CategoryService categoryService,
            ControllerHelperService helperService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.helperService = helperService;
    }

    @PostMapping(ApiPaths.PRODUCTS)
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto) {
        Long brandId = this.helperService.getCurrentBrandId();
        Product newProduct = productService.createProduct(brandId, requestDto);
        return new ResponseEntity<>(ProductResponseDto.fromEntity(newProduct), HttpStatus.CREATED);
    }

    /**
     * 為指定品牌建立一個新的商品分類。
     * 只有品牌管理員可以執行此操作。
     */
    @PostMapping("/categories")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    public ResponseEntity<CategoryResponseDto> createCategory(@Valid @RequestBody CreateCategoryRequestDto requestDto) {
        Long brandId = this.helperService.getCurrentBrandId();
        Category newCategory = categoryService.createCategory(brandId, requestDto);
        return new ResponseEntity<>(CategoryResponseDto.fromEntity(newCategory), HttpStatus.CREATED);
    }

    @GetMapping(ApiPaths.PRODUCTS + "/summary")
    public ResponseEntity<List<ProductSummaryDto>> getProductSummaries() {
        Long brandId = this.helperService.getCurrentBrandId();
        List<ProductSummaryDto> products = productService.getAvailableSummaries(brandId);
        return ResponseEntity.ok(products);
    }

    @GetMapping(ApiPaths.PRODUCTS + "/pos")
    public ResponseEntity<List<ProductPosDto>> getProductForPos() {
        Long brandId = this.helperService.getCurrentBrandId();
        List<ProductPosDto> products = productService.getAvailableProductsForPos(brandId);
        return ResponseEntity.ok(products);
    }

    @PutMapping(ApiPaths.PRODUCTS + "/{productId}/option-groups")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponseDto> linkOptionGroupsToProduct(
            @PathVariable Long productId,
            @RequestBody Set<Long> groupIds) { // 傳入 OptionGroup ID 列表
        Long brandId = this.helperService.getCurrentBrandId();
        Product updatedProduct = productService.linkOptionGroupsToProduct(brandId, productId, groupIds);
        // 這裡需要修改 ProductResponseDto 來回傳 OptionGroups
        return ResponseEntity.ok(ProductResponseDto.fromEntity(updatedProduct));
    }

}
