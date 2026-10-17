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
     * Domain Model → 全新的 JPA Entity（僅用於「新增」門市）。
     *
     * 修正(P0-1 同類問題)：原本不論新增或更新都 new 一個 StoreEntity 再 setId(既有 ID)。
     * BaseTsidEntity 的 isNew 預設 true，save() 會走 persist()，對「已存在的列」必然 PK 衝突或
     * NonUniqueObjectException。更新請改走 {@link #applyTo}（取得受管實體後套用欄位）。
     *
     * @param domain   Domain Store Aggregate
     * @param brandRef 品牌關聯參照（EntityManager.getReference 取得，不觸發查詢）
     * @return 新的 JPA Store Entity，若 domain 為 null 回傳 null
     */
    public static tw.niels.beverage_api_project.modules.store.entity.StoreEntity toNewEntity(
            Store domain, tw.niels.beverage_api_project.modules.brand.entity.BrandEntity brandRef) {
        if (domain == null) return null;

        var jpa = new tw.niels.beverage_api_project.modules.store.entity.StoreEntity();
        // BaseTsidEntity 建構時已自動產生 TSID；若 Domain 已指定 ID（例如資料匯入）則沿用
        if (domain.getId() != null) {
            jpa.setId(domain.getId());
        }
        jpa.setBrand(brandRef);
        applyTo(jpa, domain);
        return jpa;
    }

    /**
     * 把 Domain 的可變欄位套用到「受管」的 JPA Entity（更新用）。
     * 不動 id 與 brand（門市不能換品牌），由 Hibernate dirty checking 在 commit 時寫入。
     *
     * @param target 已載入的受管 StoreEntity
     * @param domain Domain Store Aggregate
     */
    public static void applyTo(tw.niels.beverage_api_project.modules.store.entity.StoreEntity target, Store domain) {
        target.setName(domain.getName());
        target.setAddress(domain.getAddress());
        target.setPhoneNumber(domain.getPhoneNumber());
        target.setIsActive(domain.isActive());
    }
}
