package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByBrand_IdAndNameIgnoreCase(Long brandId, String name);

    List<Category> findByBrand_Id(Long brandId);
    /**
     * 提供一個安全的 "findAllById" 替代方案
     */
    Set<Category> findByBrand_IdAndIdIn(Long brandId, Set<Long> categoryIds);
    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndCategoryId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<Category> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndCategoryId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<Category> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用 findByBrand_BrandId() 以確保資料隔離");
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
