package tw.niels.beverage_api_project.modules.brand.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.brand.domain.model.Brand;
import tw.niels.beverage_api_project.modules.brand.domain.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.brand.infrastructure.mapper.BrandMapper;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Brand BC Domain Repository 的 Infrastructure 層實現
 *
 * 處理流程：
 * 1. 查詢：JPA 查 Brand + 可選的 BrandPointConfig → Mapper 轉 Domain Model
 * 2. 儲存：Mapper 轉 JPA Entity → 先儲存 Brand，再儲存 BrandPointConfig
 * ============================================================
 */
@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;
    private final BrandPointConfigJpaRepository pointConfigRepository;

    /**
     * 建構子注入
     *
     * @param brandJpaRepository   Spring Data JPA 品牌倉儲
     * @param pointConfigRepository Spring Data JPA 積點設定倉儲
     */
    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository,
                                BrandPointConfigJpaRepository pointConfigRepository) {
        this.brandJpaRepository = brandJpaRepository;
        this.pointConfigRepository = pointConfigRepository;
    }

    /**
     * 依 Brand ID 查詢品牌（含積點設定）
     *
     * 流程：
     * 1. 查詢 brands 表
     * 2. 查詢 brand_point_configs 表（若無設定，Mapper 使用預設值）
     * 3. 合併為 Domain Model
     *
     * @param brandId 品牌 ID
     * @return 含 BrandPointConfig 的 Domain Brand Optional
     */
    @Override
    public Optional<Brand> findById(Long brandId) {
        return brandJpaRepository.findById(brandId)
            .map(jpaBrand -> {
                // 查詢對應的積點設定（可能尚未設定，允許 null）
                var jpaConfig = pointConfigRepository.findById(brandId).orElse(null);
                return BrandMapper.toDomain(jpaBrand, jpaConfig);
            });
    }

    /**
     * 查詢所有啟用中的品牌（含積點設定）
     *
     * 流程：
     * 1. 查詢所有 isActive=true 的品牌
     * 2. 逐一查詢積點設定（N+1 問題可接受，品牌數量通常極少）
     * 3. 轉換為 Domain Model 清單
     *
     * @return 啟用中的 Domain Brand 清單
     */
    @Override
    public List<Brand> findAllActive() {
        return brandJpaRepository.findByIsActiveTrue()
            .stream()
            .map(jpaBrand -> {
                var jpaConfig = pointConfigRepository.findById(jpaBrand.getId()).orElse(null);
                return BrandMapper.toDomain(jpaBrand, jpaConfig);
            })
            .collect(Collectors.toList());
    }

    /**
     * 儲存品牌（含積點設定）
     *
     * 流程：
     * 1. 儲存 Brand（brands 表）
     * 2. 若有 pointConfig，儲存 BrandPointConfig（brand_point_configs 表）
     * 3. 若為新增，使用反射同步資料庫生成的 ID 回 Domain Model
     *
     * @param brand 要儲存的 Domain Brand
     */
    @Override
    @Transactional
    public void save(Brand brand) {
        // 轉換並儲存 Brand Entity
        var jpaEntity = BrandMapper.toEntity(brand);
        var savedEntity = brandJpaRepository.save(jpaEntity);

        // 若是新增（無 ID），同步資料庫生成的 TSID 回 Domain Model
        if (brand.getId() == null && savedEntity.getId() != null) {
            try {
                // Brand.id 是 final，透過反射設定
                var setIdMethod = Brand.class.getDeclaredMethod("setId", Long.class);
                setIdMethod.setAccessible(true);
                setIdMethod.invoke(brand, savedEntity.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步品牌 ID 回 Domain Model：" + e.getMessage());
            }
        }

        // 儲存 BrandPointConfig（若 pointConfig 存在）
        if (brand.getPointConfig() != null && savedEntity.getId() != null) {
            // 查詢現現有設定（UPDATE）或建立新設定（INSERT）
            var existingConfig = pointConfigRepository.findById(savedEntity.getId()).orElse(null);
            var configEntity = BrandMapper.toPointConfigEntity(brand, existingConfig);
            if (configEntity != null) {
                // 確保 brand 關聯使用正確的儲存後 ID
                var brandProxy = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
                brandProxy.setId(savedEntity.getId());
                configEntity.setBrand(brandProxy);
                pointConfigRepository.save(configEntity);
            }
        }
    }
}
