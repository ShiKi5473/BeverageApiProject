package tw.niels.beverage_api_project.modules.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * 查詢指定品牌、指定商品的所有「未刪除」規格
     * 修改：增加 AndIsDeletedFalse
     */
    List<ProductVariant> findByProduct_Brand_IdAndProduct_IdAndIsDeletedFalse(Long brandId, Long productId);

    /**
     * 查詢指定品牌下的單一「未刪除」規格
     * 修改：增加 AndIsDeletedFalse
     */
    Optional<ProductVariant> findByProduct_Brand_IdAndIdAndIsDeletedFalse(Long brandId, Long id);

    // 查詢指定品牌下的一組規格 ID (且未刪除)
    List<ProductVariant> findByProduct_Brand_IdAndIdInAndIsDeletedFalse(Long brandId, Collection<Long> ids);


    /**
     * 查詢指定品牌下所有「未刪除」的規格
     * 用於：後台列表、或測試時抓取該品牌任意有效規格
     */
    List<ProductVariant> findByProduct_Brand_IdAndIsDeletedFalse(Long brandId);

    // ==========================================
    // 管理/內部/Seeder 用 (包含已刪除)
    // ==========================================

    /**
     * 查詢指定品牌下的單一規格 (不論是否刪除)
     * 用途：DataSeeder 檢查 ID 是否存在、或後台管理員恢復已刪除資料用
     */
    Optional<ProductVariant> findByProduct_Brand_IdAndId(Long brandId, Long id);

    // --- 安全防護：禁用預設不分租戶的查詢方法 ---

    @Deprecated
    @Override
    @NonNull
    default Optional<ProductVariant> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByProduct_Brand_IdAndId() 以確保品牌隔離");
    }

    @Deprecated
    @Override
    @NonNull
    default List<ProductVariant> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByProduct_Brand_IdAndProduct_Id() 以確保品牌隔離");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()，請先查詢驗證 BrandId 後再刪除");
    }

    @Deprecated
    @Override
    default boolean existsById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 existsById()，請改用包含 BrandId 的查詢");
    }
}