package tw.niels.beverage_api_project.modules.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;

@Repository
public interface OptionGroupJpaRepository extends JpaRepository<OptionGroupEntity, Long> {
    List<OptionGroupEntity> findByBrand_Id(Long brandId);

    Optional<OptionGroupEntity> findByBrand_IdAndId(Long brandId, Long groupId);

    // 根據品牌 ID 與名稱查詢 (用於 DataSeeder 防呆)
    Optional<OptionGroupEntity> findByBrand_IdAndName(Long brandId, String name);

    /**
     * 新增：依品牌批次查詢多個選項群組（品牌隔離的 findAllById 替代方案）。
     * 用於商品連結選項群組時一次驗證並取得所有群組，避免逐筆查詢。
     */
    List<OptionGroupEntity> findByBrand_IdAndIdIn(Long brandId, Collection<Long> groupIds);

    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndGroupId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<OptionGroupEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndGroupId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<OptionGroupEntity> findAll() {
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
