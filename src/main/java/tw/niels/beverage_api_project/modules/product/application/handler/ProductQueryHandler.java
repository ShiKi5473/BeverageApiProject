package tw.niels.beverage_api_project.modules.product.application.handler;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.product.application.query.*;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.service.CategoryService;
import tw.niels.beverage_api_project.modules.product.service.ProductService;

import java.util.List;

/**
 * 商品相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收查詢條件（商品摘要、POS 商品列表、分類列表）
 * 2. 委派 ProductService / CategoryService 執行查詢
 * 3. 快取結果（product-summary、product-pos 以 brandId 為 key）
 */
@Component
public class ProductQueryHandler {

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductQueryHandler(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-summary", key = "#query.brandId()")
    public List<ProductSummaryDto> handle(GetProductSummariesQuery query) {
        return productService.getAvailableSummaries(query.brandId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-pos", key = "#query.brandId()")
    public List<ProductPosDto> handle(GetProductForPosQuery query) {
        return productService.getAvailableProductsForPos(query.brandId());
    }

    @Transactional(readOnly = true)
    public List<Category> handle(GetCategoriesQuery query) {
        return categoryService.getCategoriesByBrand(query.brandId());
    }
}
