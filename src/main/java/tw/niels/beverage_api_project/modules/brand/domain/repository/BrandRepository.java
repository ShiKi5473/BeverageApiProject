package tw.niels.beverage_api_project.modules.brand.domain.repository;

import tw.niels.beverage_api_project.modules.brand.domain.model.Brand;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Domain - Repository Interface
 * ============================================================
 *
 * 品牌倉儲介面
 * ============================================================
 */
public interface BrandRepository {

    Optional<Brand> findById(Long brandId);
    List<Brand> findAllActive();
    void save(Brand brand);
}
