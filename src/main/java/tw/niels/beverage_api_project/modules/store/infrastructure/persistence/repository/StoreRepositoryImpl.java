package tw.niels.beverage_api_project.modules.store.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.store.domain.model.Store;
import tw.niels.beverage_api_project.modules.store.domain.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.infrastructure.mapper.StoreMapper;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Store BC Domain Repository 的 Infrastructure 層實現
 * ============================================================
 */
@Repository
public class StoreRepositoryImpl implements StoreRepository {

    private final StoreJpaRepository jpaRepository;

    // 用於取得品牌關聯的 getReference 代理（不觸發查詢）
    @PersistenceContext
    private EntityManager entityManager;

    public StoreRepositoryImpl(StoreJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * 依品牌 ID 與門市 ID 查詢門市
     *
     * @param brandId 品牌 ID（多租戶隔離）
     * @param storeId 門市 ID
     * @return Domain Store Optional
     */
    @Override
    public Optional<Store> findById(Long brandId, Long storeId) {
        return jpaRepository.findByBrand_IdAndId(brandId, storeId)
            .map(StoreMapper::toDomain);
    }

    /**
     * 查詢品牌下所有門市
     *
     * @param brandId 品牌 ID
     * @return Domain Store 清單
     */
    @Override
    public List<Store> findByBrandId(Long brandId) {
        return jpaRepository.findByBrand_Id(brandId)
            .stream()
            .map(StoreMapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * 儲存門市（新增或更新）
     *
     * 修正(P0-1 同類問題 / 清理)：
     * - 更新：先以 brandId + id 載入「受管」的 StoreEntity，再套用欄位（dirty checking），
     *   不再 new 一個帶舊 ID 的 StoreEntity 去 persist（必定 PK 衝突 / NonUniqueObjectException）。
     * - 新增：以 EntityManager.getReference 取得品牌參照後建立新實體，並用 Store.assignId() 回填 ID，
     *   取代原本呼叫不存在的 private setId 的反射（失敗只記警告，導致 StoreCommandHandler 回傳 null ID）。
     *
     * @param store 要儲存的 Domain Store
     */
    @Override
    @Transactional
    public void save(Store store) {
        StoreEntity entity;
        if (store.getId() != null) {
            // 更新既有門市：帶 brandId 查詢，同時維持多租戶隔離
            entity = jpaRepository.findByBrand_IdAndId(store.getBrandId(), store.getId())
                .orElseThrow(() -> new ResourceNotFoundException("找不到分店，ID：" + store.getId()));
            StoreMapper.applyTo(entity, store);
        } else {
            // 新增門市
            entity = StoreMapper.toNewEntity(store, entityManager.getReference(BrandEntity.class, store.getBrandId()));
        }

        StoreEntity saved = jpaRepository.save(entity);

        // 若是新增（Domain 尚無 ID），把資料庫實體的 TSID 回填給 Domain Model
        if (store.getId() == null) {
            store.assignId(saved.getId());
        }
    }
}
