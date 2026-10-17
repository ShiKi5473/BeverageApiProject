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
import tw.niels.beverage_api_project.modules.product.application.query.GetProductByIdQuery;
import tw.niels.beverage_api_project.modules.product.application.query.GetProductForPosQuery;
import tw.niels.beverage_api_project.modules.product.application.query.GetProductSummariesQuery;
import tw.niels.beverage_api_project.modules.product.dto.*;

import java.util.List;
import java.util.Set;

/**
 * 商品與分類 API
 * <p>
 * 修改說明：
 * - S-1：POST /brands/products 原本沒有 @PreAuthorize（任何登入者含 MEMBER 都能建立商品與價格），
 *   改為與同為品牌層級目錄異動的「建立分類」「新增／修改規格」一致，只允許 BRAND_ADMIN。
 * - 建立商品改回傳完整的 ProductResponseDto（原本回傳 null body）。
 * - 依 API 契約開放唯讀目錄：GET /brands/products/pos、GET /brands/categories 允許員工、會員與訪客（線上點餐）。
 * - Controller 只處理 DTO（分類查詢原本拿 JPA Entity 在交易外轉換）。
 * - Tag 名稱原為批次改名殘留的 "ProductEntity Management"。
 * </p>
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
@Tag(name = "Product Management", description = "商品與分類管理 API")
public class ProductController {

    /** 新增：唯讀商品目錄可存取的角色（員工、會員、線上點餐訪客；平台管理員不屬於任何品牌，不開放） */
    private static final String CATALOG_READERS =
            "hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER', 'MEMBER', 'GUEST')";

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
    @PreAuthorize("hasRole('BRAND_ADMIN')") // S-1：補上授權，僅品牌管理員可建立商品
    @Operation(summary = "建立新商品")
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequestDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        Long productId = commandHandler.handle(new CreateProductCommand(brandId, requestDto));
        // 修改：建立（交易已提交）後再以 GetProductByIdQuery 查出完整資料回傳，含系統產生的規格 ID
        ProductResponseDto created = queryHandler.handle(new GetProductByIdQuery(brandId, productId));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
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
    @PreAuthorize(CATALOG_READERS) // 依契約：任何品牌內的登入者（含 GUEST／MEMBER）皆可讀取
    @Operation(summary = "取得 POS 完整商品資料")
    public ResponseEntity<List<ProductPosDto>> getProductForPos() {
        Long brandId = helperService.getCurrentBrandId(); // 訪客也能取得品牌（TenantPrincipal）
        return ResponseEntity.ok(queryHandler.handle(new GetProductForPosQuery(brandId)));
    }

    @PutMapping(ApiPaths.PRODUCTS + "/{productId}/option-groups")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "將客製化選項和商品選項連結")
    public ResponseEntity<Void> linkOptionGroupsToProduct(
            @PathVariable Long productId,
            @RequestBody Set<Long> groupIds) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new LinkOptionGroupsCommand(brandId, productId, groupIds));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/categories")
    @PreAuthorize(CATALOG_READERS) // 修改：依契約開放會員與訪客（原本只限員工，線上點餐頁無法載入分類）
    @Operation(summary = "取得分類列表")
    public ResponseEntity<List<CategoryResponseDto>> getCategoriesByBrand() {
        Long brandId = helperService.getCurrentBrandId();
        // 修改：QueryHandler 已在交易內轉成 DTO，Controller 不再接觸 CategoryEntity
        return ResponseEntity.ok(queryHandler.handle(new GetCategoriesQuery(brandId)));
    }

    @PostMapping(ApiPaths.PRODUCTS + "/{productId}/variants")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    @Operation(summary = "新增商品規格")
    public ResponseEntity<Void> addProductVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductVariantDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        commandHandler.handle(new AddProductVariantCommand(brandId, productId, requestDto));
        return ResponseEntity.status(HttpStatus.CREATED).build();
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
