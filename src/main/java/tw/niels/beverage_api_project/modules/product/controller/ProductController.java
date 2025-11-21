package tw.niels.beverage_api_project.modules.product.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Product Management", description = "商品與分類管理 API")
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
    @Operation(summary = "建立新商品", description = "建立品牌下的新飲品資料")
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
    @Operation(summary = "建立商品分類", description = "例如：茶類、奶類、果汁 (僅品牌管理員)")
    public ResponseEntity<CategoryResponseDto> createCategory(@Valid @RequestBody CreateCategoryRequestDto requestDto) {
        Long brandId = this.helperService.getCurrentBrandId();
        Category newCategory = categoryService.createCategory(brandId, requestDto);
        return new ResponseEntity<>(CategoryResponseDto.fromEntity(newCategory), HttpStatus.CREATED);
    }

    @GetMapping(ApiPaths.PRODUCTS + "/summary")
    @Operation(summary = "取得商品列表 (摘要版)", description = "回傳簡易的商品資訊，用於列表顯示")
    public ResponseEntity<List<ProductSummaryDto>> getProductSummaries() {
        Long brandId = this.helperService.getCurrentBrandId();
        List<ProductSummaryDto> products = productService.getAvailableSummaries(brandId);
        return ResponseEntity.ok(products);
    }

    @GetMapping(ApiPaths.PRODUCTS + "/pos")
    @Operation(summary = "取得 POS 完整商品資料", description = "包含商品選項、分類等完整結構，供 POS 前端使用")
    public ResponseEntity<List<ProductPosDto>> getProductForPos() {
        Long brandId = this.helperService.getCurrentBrandId();
        List<ProductPosDto> products = productService.getAvailableProductsForPos(brandId);
        return ResponseEntity.ok(products);
    }

    @PutMapping(ApiPaths.PRODUCTS + "/{productId}/option-groups")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "將客製化選項和商品選項連結")
    public ResponseEntity<ProductResponseDto> linkOptionGroupsToProduct(
            @PathVariable Long productId,
            @RequestBody Set<Long> groupIds) {
        Long brandId = this.helperService.getCurrentBrandId();
        Product updatedProduct = productService.linkOptionGroupsToProduct(brandId, productId, groupIds);
        return ResponseEntity.ok(ProductResponseDto.fromEntity(updatedProduct));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "取得分類列表")
    public ResponseEntity<List<CategoryResponseDto>> getCategoriesByBrand() {
        Long brandId = this.helperService.getCurrentBrandId();
        List<Category> categories = categoryService.getCategoriesByBrand(brandId);
        List<CategoryResponseDto> dtos = categories.stream()
                .map(dto -> CategoryResponseDto.fromEntity(dto))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

}
