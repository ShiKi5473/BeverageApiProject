package tw.niels.beverage_api_project.modules.brand.infrastructure.mapper;

import tw.niels.beverage_api_project.modules.brand.domain.model.Brand;
import tw.niels.beverage_api_project.modules.brand.domain.model.BrandPointConfig;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Brand BC - JPA Entity ↔ Domain Model 雙向映射工具
 * ============================================================
 */
public class BrandMapper {

    /**
     * JPA BrandEntity + BrandPointConfigEntity → Domain Brand
     */
    public static Brand toDomain(BrandEntity jpaBrand, BrandPointConfigEntity jpaConfig) {
        if (jpaBrand == null) return null;

        // 轉換積點配置 JPA → Domain VO
        BrandPointConfig domainConfig;
        if (jpaConfig != null) {
            domainConfig = new BrandPointConfig(
                jpaConfig.getEarnRate(),
                jpaConfig.getRedeemRate(),
                jpaConfig.getExpiryDays()
            );
        } else {
            domainConfig = BrandPointConfig.defaultSettings();
        }

        return new Brand(
            jpaBrand.getId(),
            jpaBrand.getName(),
            jpaBrand.getContactPerson(),
            domainConfig,
            Boolean.TRUE.equals(jpaBrand.getIsActive())
        );
    }

    /**
     * Domain Brand → 全新的 JPA BrandEntity（僅用於「新增」品牌）。
     *
     * 修正(P0-1 同類問題)：原本 toEntity 不論新增或更新都 new 一個 BrandEntity 再 setId(既有 ID)，
     * BaseTsidEntity 的 isNew 預設 true，save() 會走 persist()，更新既有品牌（例如修改積點設定）時
     * 必然 PK 衝突或 NonUniqueObjectException。更新請改走 {@link #applyTo}。
     */
    public static BrandEntity toNewEntity(Brand domain) {
        if (domain == null) return null;

        var jpa = new BrandEntity();
        // BaseTsidEntity 建構時已自動產生 TSID；若 Domain 已指定 ID（例如資料匯入）則沿用
        if (domain.getId() != null) {
            jpa.setId(domain.getId());
        }
        applyTo(jpa, domain);
        return jpa;
    }

    /**
     * 把 Domain 的可變欄位套用到「受管」的 BrandEntity（更新用，由 dirty checking 在 commit 時寫入）。
     */
    public static void applyTo(BrandEntity target, Brand domain) {
        target.setName(domain.getName());
        target.setContactPerson(domain.getContactPerson());
        target.setIsActive(domain.isActive());
    }

    /**
     * Domain Brand 的積點配置 → JPA BrandPointConfigEntity
     *
     * @param domain   Domain Brand
     * @param existing 資料庫中既有的設定（受管實體，可為 null 表示尚未設定）
     * @param brandRef 品牌實體（新增設定時作為 @MapsId 的關聯；呼叫端傳入受管實體，不必再 new 暫態物件）
     */
    public static BrandPointConfigEntity toPointConfigEntity(Brand domain, BrandPointConfigEntity existing, BrandEntity brandRef) {
        if (domain.getPointConfig() == null) return null;

        var jpa = existing != null
            ? existing
            : new BrandPointConfigEntity(brandRef);

        // 修正：原本對「既有」設定也會換上一個 new BrandEntity() 暫態物件，把受管實體的關聯弄成未管理狀態；
        // 現在只有新增時才設定關聯（建構子已帶入 brandRef），更新時不動關聯。
        jpa.setEarnRate(domain.getPointConfig().earnRate());
        jpa.setRedeemRate(domain.getPointConfig().redeemRate());
        jpa.setExpiryDays(domain.getPointConfig().expiryDays());
        return jpa;
    }
}
