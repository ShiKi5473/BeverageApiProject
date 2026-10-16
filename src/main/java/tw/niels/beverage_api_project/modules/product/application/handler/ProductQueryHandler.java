package tw.niels.beverage_api_project.modules.product.application.handler;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.product.application.ProductCacheNames;
import tw.niels.beverage_api_project.modules.product.application.query.*;
import tw.niels.beverage_api_project.modules.product.dto.CategoryResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.service.CategoryService;
import tw.niels.beverage_api_project.modules.product.service.ProductService;

import java.util.List;

/**
 * 商品相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收查詢條件（商品摘要、POS 商品列表、分類列表、單一商品）
 * 2. 委派 ProductService / CategoryService 執行查詢
 * 3. 快取結果（product-summary、product-pos 以 brandId 為 key）
 *
 * 修改說明：
 * - 快取名稱改回 product-summary／product-pos（批次改名殘留），並改用 {@link ProductCacheNames} 常數。
 * - 快取只保留在這一層：ProductService 原本也對同一份資料加了 @Cacheable，造成重複快取，已移除。
 *   所有清除快取的 @CacheEvict（ProductCommandHandler、OptionGroupService）都使用同一組名稱。
 * - 分類查詢改在交易內轉成 DTO 再回傳（Controller 不再接觸 JPA Entity）。
 */
@Component
public class ProductQueryHandler {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductJpaRepository productJpaRepository;

    public ProductQueryHandler(ProductService productService,
                               CategoryService categoryService,
                               ProductJpaRepository productJpaRepository) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.productJpaRepository = productJpaRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ProductCacheNames.SUMMARY, key = "#query.brandId()")
    public List<ProductSummaryDto> handle(GetProductSummariesQuery query) {
        return productService.getAvailableSummaries(query.brandId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ProductCacheNames.POS, key = "#query.brandId()")
    public List<ProductPosDto> handle(GetProductForPosQuery query) {
        return productService.getAvailableProductsForPos(query.brandId());
    }

    /** 修改：回傳 DTO（原本回傳 List<CategoryEntity>，由 Controller 在交易外轉換） */
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> handle(GetCategoriesQuery query) {
        return categoryService.getCategoriesByBrand(query.brandId());
    }

    /**
     * 新增：查詢單一商品詳細資料（品牌隔離），在交易內完成 DTO 轉換以載入延遲關聯。
     */
    @Transactional(readOnly = true)
    public ProductResponseDto handle(GetProductByIdQuery query) {
        return productJpaRepository.findByBrand_IdAndId(query.brandId(), query.productId())
                .map(ProductResponseDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + query.productId()));
    }
}
