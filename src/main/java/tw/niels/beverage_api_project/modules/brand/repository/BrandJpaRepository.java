package tw.niels.beverage_api_project.modules.brand.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Infrastructure - Spring Data JPA Repository
 * ============================================================
 *
 * 品牌 JPA 倉儲介面
 * 重命名自 BrandRepository，以區別 domain/repository/BrandRepository 介面。
 * 直接操作 JPA BrandEntity Entity，由 BrandRepositoryImpl 委派至此。
 * ============================================================
 */
@Repository
public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByName(String name);

    List<BrandEntity> findByIsActiveTrue();

}
