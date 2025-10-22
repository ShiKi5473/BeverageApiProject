package tw.niels.beverage_api_project.modules.product.controller;

import java.util.List;

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

    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @PostMapping("/{brandId}" + ApiPaths.PRODUCTS)
    public ResponseEntity<ProductResponseDto> createProduct(@PathVariable Long brandId,
            @Valid @RequestBody CreateProductRequestDto requestDto) {
        Product newProduct = productService.createProduct(brandId, requestDto);
        return new ResponseEntity<>(ProductResponseDto.fromEntity(newProduct), HttpStatus.CREATED);
    }

    /**
     * 為指定品牌建立一個新的商品分類。
     * 只有品牌管理員可以執行此操作。
     */
    @PostMapping("/{brandId}/cate   gories")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    public ResponseEntity<Category> createCategory(@PathVariable Long brandId,
            @Valid @RequestBody CreateCategoryRequestDto requestDto) {
        Category newCategory = categoryService.createCategory(brandId, requestDto);
        return new ResponseEntity<>(newCategory, HttpStatus.CREATED);
    }

    @GetMapping("/{brandId}" + ApiPaths.PRODUCTS + "/summary")
    public ResponseEntity<List<ProductSummaryDto>> getProductSummaries(@PathVariable Long brandId) {
        List<ProductSummaryDto> products = productService.getAvailableSummaries(brandId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{brandId}" + ApiPaths.PRODUCTS + "/pos")
    public ResponseEntity<List<ProductPosDto>> getProductForPos(@PathVariable Long brandId) {
        List<ProductPosDto> products = productService.getAvailableProductsForPos(brandId);
        return ResponseEntity.ok(products);
    }

}
