package tw.niels.beverage_api_project.modules.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * POS 專用查詢：一次抓取商品及其所有關聯 (Category, OptionGroup, ProductOption)
     * 使用 LEFT JOIN FETCH 解決 N+1 問題
     * * 注意：
     * 1. 使用 Set 集合 (在 Entity 中) 可以避免 MultipleBagFetchException。
     * 2. 如果資料量非常大，這種 Cartesian Product (笛卡兒積) 可能會影響效能，
     * 但在 POS 菜單場景下 (通常幾百筆商品)，這是最快且最乾淨的解法。
     */
    @Query("""
        SELECT DISTINCT p
        FROM Product p 
        LEFT JOIN FETCH p.categories 
        LEFT JOIN FETCH p.optionGroups og 
        LEFT JOIN FETCH og.options 
        WHERE p.brand.id = :brandId 
        AND p.status = :status
    """)
    List<Product> findByBrand_IdAndStatus(
            @Param("brandId") Long brandId,
            @Param("status") ProductStatus status
    );

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.categories LEFT JOIN FETCH p.optionGroups WHERE p.brand.id = :brandId AND p.id = :id")
    Optional<Product> findByBrand_IdAndId(@Param("brandId") Long brandId, @Param("id") Long id);

    Optional<Product> findByBrand_IdAndName(Long brandId, String name);


    // 批次查詢商品 (包含關聯，避免後續存取 snapshot 再次查詢)
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.categories " +
            "LEFT JOIN FETCH p.optionGroups " +
            "WHERE p.brand.id = :brandId AND p.id IN :ids")
    List<Product> findByBrand_IdAndIdIn(@Param("brandId") Long brandId, @Param("ids") Collection<Long> ids);


    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndProductId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<Product> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndProductId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<Product> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用包含 brandId 的自訂查詢");
    }

    /**
     * 禁用預設的 deleteById。
     */
    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 deleteById()，請先驗證 BrandId 再進行刪除");
    }

    /**
     * 禁用預設的 existsById。
     */
    @Deprecated
    @Override
    default boolean existsById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 existsById()，請改用包含 brandId 的自訂查詢");
    }
}
