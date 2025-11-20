package tw.niels.beverage_api_project.modules.brand.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfig;

@Repository
public interface BrandPointConfigRepository extends JpaRepository<BrandPointConfig, Long> {

}