package tw.niels.beverage_api_project.modules.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.store.entity.Store;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByBrand_BrandIdAndStoreId(Long brandId, Long storeId);

}
