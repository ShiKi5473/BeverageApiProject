package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;

@Repository
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByBrand_IdAndNameIgnoreCase(Long brandId, String name);

    List<CategoryEntity> findByBrand_IdAndIsActive(Long brandId, Boolean isActive);

    List<CategoryEntity> findByBrand_Id(Long brandId);
    Optional<CategoryEntity> findByBrand_IdAndId(Long brandId, Long id);
    /**
     * 提供一個安全的 "findAllById" 替代方案
     */
    Set<CategoryEntity> findByBrand_IdAndIdIn(Long brandId, Set<Long> categoryIds);
    /**
     * 禁用預設的 findById，強迫使用 findByBrand_IdAndId()。
     * （修改：訊息中的方法名原為改名前的 findByBrand_BrandIdAndXxxId，已更正為實際存在的方法）
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<CategoryEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_IdAndId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<CategoryEntity> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用 findByBrand_Id() 以確保資料隔離");
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
