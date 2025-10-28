package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByBrand_BrandId(Long brandId);

    Optional<OptionGroup> findByBrand_BrandIdAndGroupId(Long brandId, Long groupId);
}
