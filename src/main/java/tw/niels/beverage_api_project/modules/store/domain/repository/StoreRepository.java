package tw.niels.beverage_api_project.modules.store.domain.repository;

import tw.niels.beverage_api_project.modules.store.domain.model.Store;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 門市倉儲介面
 * ============================================================
 */
public interface StoreRepository {

    Optional<Store> findById(Long brandId, Long storeId);
    List<Store> findByBrandId(Long brandId);
    void save(Store store);
}
