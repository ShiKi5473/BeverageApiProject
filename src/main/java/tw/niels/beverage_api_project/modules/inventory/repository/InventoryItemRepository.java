package tw.niels.beverage_api_project.modules.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByBrand_Id(Long brandId);

    Optional<InventoryItem> findByBrand_IdAndName(Long brandId, String name);

    // 一次查詢多個 ID
    List<InventoryItem> findByBrand_IdAndIdIn(Long brandId, Collection<Long> ids);

    /**
     * 取得原物料並加寫入鎖 (SELECT FOR UPDATE)
     * 用於扣減庫存時，鎖定該品項，防止死鎖與超賣。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.id = :id AND i.brand.id = :brandId")
    Optional<InventoryItem> findByBrandIdAndIdForUpdate(@Param("brandId") Long brandId, @Param("id") Long id);

    // 安全的單筆查詢
    Optional<InventoryItem> findByBrand_IdAndId(Long brandId, Long id);

    // --- 安全防護：禁用預設不分租戶的查詢方法 ---

    @Deprecated
    @Override
    @NonNull
    default Optional<InventoryItem> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByBrand_BrandIdAndId() 以確保品牌隔離");
    }

    @Deprecated
    @Override
    @NonNull
    default List<InventoryItem> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByBrand_BrandId() 以確保品牌隔離");
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