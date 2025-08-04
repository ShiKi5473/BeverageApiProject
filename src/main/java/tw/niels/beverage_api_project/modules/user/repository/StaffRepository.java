package tw.niels.beverage_api_project.modules.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByBrand_BrandIdAndUsername(Long brandId, String username);

    List<Staff> findByBrand_BrandIdAndRole(Long brandId, StaffRole role);
}
