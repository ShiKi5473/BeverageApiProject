package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByBrand_Id(Long brandId);

    Optional<OptionGroup> findByBrand_IdAndId(Long brandId, Long groupId);

    // 根據品牌 ID 與名稱查詢 (用於 DataSeeder 防呆)
    Optional<OptionGroup> findByBrand_IdAndName(Long brandId, String name);

    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndGroupId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<OptionGroup> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndGroupId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<OptionGroup> findAll() {
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
