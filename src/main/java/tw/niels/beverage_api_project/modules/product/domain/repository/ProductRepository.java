package tw.niels.beverage_api_project.modules.product.domain.repository;

import tw.niels.beverage_api_project.modules.product.domain.model.Category;
import tw.niels.beverage_api_project.modules.product.domain.model.OptionGroup;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 商品目錄倉儲介面
 *
 * 定義 Domain Layer 對商品、分類與選項群組的存取需求。
 * 實作由 Infrastructure Layer 提供。
 * ============================================================
 */
public interface ProductRepository {

    // ─── Product 相關 ───────────────────────────────────────────────────

    Optional<Product> findProductById(Long brandId, Long productId);
    List<Product> findActiveProductsByBrand(Long brandId);
    void saveProduct(Product product);

    // ─── Category 相關 ──────────────────────────────────────────────────

    Optional<Category> findCategoryById(Long brandId, Long categoryId);
    List<Category> findActiveCategoriesByBrand(Long brandId);
    void saveCategory(Category category);

    // ─── OptionGroup 相關 ───────────────────────────────────────────────

    Optional<OptionGroup> findOptionGroupById(Long brandId, Long groupId);
    List<OptionGroup> findOptionGroupsByBrand(Long brandId);
    void saveOptionGroup(OptionGroup optionGroup);
}
