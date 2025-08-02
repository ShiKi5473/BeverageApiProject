package tw.niels.beverage_api_project.modules.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.user.entity.Staff;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long>{

    Optional<Staff> findByBrand_BrandIdAndUsername(Long brandId, String username);
}
