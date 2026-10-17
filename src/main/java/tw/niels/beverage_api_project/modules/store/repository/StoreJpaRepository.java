package tw.niels.beverage_api_project.modules.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Spring Data JPA Repository
 * ============================================================
 *
 * 門市 JPA 倉儲介面
 * 重命名自 StoreRepository，以區別 domain/repository/StoreRepository 介面。
 * ============================================================
 */
@Repository
public interface StoreJpaRepository extends JpaRepository<StoreEntity, Long> {

    Optional<StoreEntity> findByBrand_IdAndId(Long brandId, Long storeId);

    List<StoreEntity> findByBrand_Id(Long brandId);

    // 只查營業中的分店（會員／訪客的線上點餐選店用）
    List<StoreEntity> findByBrand_IdAndIsActiveTrue(Long brandId);

    List<StoreEntity> findByBrand_IdAndIdIn(Long brandId, Collection<Long> storeIds);

    @Query("SELECT s FROM StoreEntity s WHERE s.id = :id")
    Optional<StoreEntity> findByIdSystem(Long id);  // 系統專用查詢（繞過 brandId 限制）

    @Query("SELECT s FROM StoreEntity s")
    Page<StoreEntity> findAllStoresForSystem(Pageable pageable);  // 系統排程專用

    @Query("SELECT s.id AS storeId, s.brand.id AS brandId FROM StoreEntity s")
    Page<StoreIdentity> findAllStoreIdentities(Pageable pageable);  // 報表用投影查詢

}
