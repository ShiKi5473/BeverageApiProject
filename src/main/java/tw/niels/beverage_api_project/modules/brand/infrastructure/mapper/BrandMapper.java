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
     * Domain Brand → JPA BrandEntity
     */
    public static BrandEntity toEntity(Brand domain) {
        if (domain == null) return null;

        var jpa = new BrandEntity();
        if (domain.getId() != null) {
            jpa.setId(domain.getId());
        }
        jpa.setName(domain.getName());
        jpa.setContactPerson(domain.getContactPerson());
        jpa.setIsActive(domain.isActive());
        return jpa;
    }

    /**
     * Domain Brand 的積點配置 → JPA BrandPointConfigEntity
     */
    public static BrandPointConfigEntity toPointConfigEntity(Brand domain, BrandPointConfigEntity existing) {
        if (domain.getPointConfig() == null) return null;

        var jpa = existing != null
            ? existing
            : new BrandPointConfigEntity();

        // 透過 Proxy 設定 Brand 關聯（僅帶入 ID）
        if (domain.getId() != null) {
            var brandProxy = new BrandEntity();
            brandProxy.setId(domain.getId());
            jpa.setBrand(brandProxy);
        }
        jpa.setEarnRate(domain.getPointConfig().earnRate());
        jpa.setRedeemRate(domain.getPointConfig().redeemRate());
        jpa.setExpiryDays(domain.getPointConfig().expiryDays());
        return jpa;
    }
}
