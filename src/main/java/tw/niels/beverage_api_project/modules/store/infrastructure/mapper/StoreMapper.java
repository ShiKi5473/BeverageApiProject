package tw.niels.beverage_api_project.modules.store.infrastructure.mapper;

import tw.niels.beverage_api_project.modules.store.domain.model.Store;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Store BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 映射規則：
 * - brandId：jpa.getBrand().getId()（取 ID，不載入整個 Brand Entity）
 * - isActive：對應 stores 表的 is_active 欄位
 * ============================================================
 */
public class StoreMapper {

    /**
     * JPA Entity → Domain Model
     *
     * @param jpaStore stores 表的 JPA Entity
     * @return Domain Store，若 jpaStore 為 null 回傳 null
     */
    public static Store toDomain(tw.niels.beverage_api_project.modules.store.entity.StoreEntity jpaStore) {
        if (jpaStore == null) return null;

        return new Store(
            jpaStore.getId(),
            // Brand 關聯取 ID（避免載入完整 Brand Entity）
            jpaStore.getBrand() != null ? jpaStore.getBrand().getId() : null,
            jpaStore.getName(),
            jpaStore.getAddress(),
            jpaStore.getPhoneNumber(),
            // 使用 JPA Entity 中的 is_active 欄位
            Boolean.TRUE.equals(jpaStore.getIsActive())
        );
    }

    /**
     * Domain Model → JPA Entity
     *
     * @param domain Domain Store Aggregate
     * @return JPA Store Entity，若 domain 為 null 回傳 null
     */
    public static tw.niels.beverage_api_project.modules.store.entity.StoreEntity toEntity(Store domain) {
        if (domain == null) return null;

        var jpa = new tw.niels.beverage_api_project.modules.store.entity.StoreEntity();
        // 設定 ID（若已有 ID 則為更新操作）
        if (domain.getId() != null) {
            jpa.setId(domain.getId());
        }

        // Brand 關聯：使用 Proxy 物件，僅帶入 ID，避免額外查詢
        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpa.setBrand(brand);
        }

        jpa.setName(domain.getName());
        jpa.setAddress(domain.getAddress());
        jpa.setPhoneNumber(domain.getPhoneNumber());
        jpa.setIsActive(domain.isActive());
        return jpa;
    }
}
