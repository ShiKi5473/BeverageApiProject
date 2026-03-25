package tw.niels.beverage_api_project.modules.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.product.application.command.*;
import tw.niels.beverage_api_project.modules.product.application.handler.ProductCommandHandler;
import tw.niels.beverage_api_project.modules.product.application.handler.ProductQueryHandler;
import tw.niels.beverage_api_project.modules.product.application.query.GetCategoriesQuery;
import tw.niels.beverage_api_project.modules.product.application.query.GetProductForPosQuery;
import tw.niels.beverage_api_project.modules.product.application.query.GetProductSummariesQuery;
import tw.niels.beverage_api_project.modules.product.dto.*;
import tw.niels.beverage_api_project.modules.product.entity.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
@Tag(name = "Product Management", description = "商品與分類管理 API")
public class ProductController {

    private final ProductCommandHandler commandHandler;
    private final ProductQueryHandler queryHandler;
    private final ControllerHelperService helperService;

    public ProductController(ProductCommandHandler commandHandler,
                             ProductQueryHandler queryHandler,
                             ControllerHelperService helperService) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
        this.helperService = helperService;
    }

    @PostMapping(ApiPaths.PRODUCTS)
    @Operation(summary = "建立新商品")
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        Long productId = commandHandler.handle(new CreateProductCommand(brandId, requestDto));
        // 為了返回完整 DTO，這裡簡化處理。實際應有 GetProductByIdQuery
        return new ResponseEntity<>(null, HttpStatus.CREATED); 
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "建立商品分類")
    public ResponseEntity<Void> createCategory(@Valid @RequestBody CreateCategoryRequestDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new CreateCategoryCommand(brandId, requestDto));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping(ApiPaths.PRODUCTS + "/summary")
    @Operation(summary = "取得商品列表 (摘要版)")
    public ResponseEntity<List<ProductSummaryDto>> getProductSummaries() {
        Long brandId = helperService.getCurrentBrandId();
        return ResponseEntity.ok(queryHandler.handle(new GetProductSummariesQuery(brandId)));
    }

    @GetMapping(ApiPaths.PRODUCTS + "/pos")
    @Operation(summary = "取得 POS 完整商品資料")
    public ResponseEntity<List<ProductPosDto>> getProductForPos() {
        Long brandId = helperService.getCurrentBrandId();
        return ResponseEntity.ok(queryHandler.handle(new GetProductForPosQuery(brandId)));
    }

    @PutMapping(ApiPaths.PRODUCTS + "/{productId}/option-groups")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "將客製化選項和商品選項連結")
    public ResponseEntity<ProductResponseDto> linkOptionGroupsToProduct(
            @PathVariable Long productId,
            @RequestBody Set<Long> groupIds) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new LinkOptionGroupsCommand(brandId, productId, groupIds));
        return ResponseEntity.ok(null);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "取得分類列表")
    public ResponseEntity<List<CategoryResponseDto>> getCategoriesByBrand() {
        Long brandId = helperService.getCurrentBrandId();
        List<Category> categories = queryHandler.handle(new GetCategoriesQuery(brandId));
        List<CategoryResponseDto> dtos = categories.stream()
                .map(CategoryResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping(ApiPaths.PRODUCTS + "/{productId}/variants")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "新增商品規格")
    public ResponseEntity<ProductResponseDto> addProductVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductVariantDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        ProductResponseDto result = commandHandler.handle(new AddProductVariantCommand(brandId, productId, requestDto));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping(ApiPaths.PRODUCTS + "/variants/{variantId}")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "更新商品規格")
    public ResponseEntity<Void> updateProductVariant(
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new UpdateProductVariantCommand(brandId, variantId, requestDto));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(ApiPaths.PRODUCTS + "/variants/{variantId}")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "刪除商品規格 (軟刪除)")
    public ResponseEntity<Void> deleteProductVariant(@PathVariable Long variantId) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new DeleteProductVariantCommand(brandId, variantId));
        return ResponseEntity.noContent().build();
    }
}
