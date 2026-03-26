package tw.niels.beverage_api_project.modules.store.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.store.domain.model.Store;
import tw.niels.beverage_api_project.modules.store.domain.repository.StoreRepository;
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
     * 儲存門市
     *
     * @param store 要儲存的 Domain Store
     */
    @Override
    public void save(Store store) {
        var jpaEntity = StoreMapper.toEntity(store);
        var savedEntity = jpaRepository.save(jpaEntity);

        // 若是新增（無 ID），同步資料庫生成的 TSID 回 Domain Model
        if (store.getId() == null && savedEntity.getId() != null) {
            try {
                var setIdMethod = Store.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(store, savedEntity.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步門市 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }
}
