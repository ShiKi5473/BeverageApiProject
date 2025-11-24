package tw.niels.beverage_api_project.modules.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.store.entity.Store;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByBrand_BrandIdAndStoreId(Long brandId, Long storeId);

    /**
     * 【系統排程專用】查詢系統內所有分店。
     * <p>此方法專供報表排程使用，繞過 findAll() 的安全限制。</p>
     */
    @Query("SELECT s FROM Store s")
    Page<Store> findAllStoresForSystem(Pageable pageable);

    List<Store> findByBrand_BrandId(Long brandId);

    List<Store> findByBrand_BrandIdAndStoreIdIn(Long brandId, Collection<Long> storeIds);


    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndStoreId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<Store> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndStoreId() 以確保資料隔離");
    }

    @Deprecated
    @Override
    @NonNull
    default List<Store> findAllById(@NonNull Iterable<Long> ids) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAllById()，請改用 findByBrand_BrandIdAndStoreIdIn() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<Store> findAll() {
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
