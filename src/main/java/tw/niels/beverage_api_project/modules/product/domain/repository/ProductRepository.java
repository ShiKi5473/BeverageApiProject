package tw.niels.beverage_api_project.modules.product.domain.repository;

import tw.niels.beverage_api_project.modules.product.domain.model.Category;
import tw.niels.beverage_api_project.modules.product.domain.model.OptionGroup;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /** 透過規格 ID 反查所屬商品（供只有 variantId 的命令使用） */
    Optional<Product> findProductByVariantId(Long brandId, Long variantId);

    List<Product> findActiveProductsByBrand(Long brandId);

    /**
     * 儲存商品聚合。
     * 新商品（id 為 null）會新建並回填 ID；既有商品會把 Domain 狀態套用到同交易中的受管實體。
     */
    void saveProduct(Product product);

    // ─── Category 相關 ──────────────────────────────────────────────────

    Optional<Category> findCategoryById(Long brandId, Long categoryId);
    List<Category> findActiveCategoriesByBrand(Long brandId);
    void saveCategory(Category category);

    /** 新增：同品牌下是否已有同名分類（忽略大小寫），供建立分類前檢查重複 */
    boolean existsCategoryName(Long brandId, String name);

    /** 新增：回傳指定 ID 中「確實屬於此品牌」的分類 ID（批次查詢，用於驗證請求中的分類 ID） */
    Set<Long> findExistingCategoryIds(Long brandId, Collection<Long> categoryIds);

    // ─── OptionGroup 相關 ───────────────────────────────────────────────

    Optional<OptionGroup> findOptionGroupById(Long brandId, Long groupId);
    List<OptionGroup> findOptionGroupsByBrand(Long brandId);
    void saveOptionGroup(OptionGroup optionGroup);

    /** 新增：回傳指定 ID 中「確實屬於此品牌」的選項群組 ID（批次查詢，取代逐筆載入整個群組） */
    Set<Long> findExistingOptionGroupIds(Long brandId, Collection<Long> groupIds);
}
