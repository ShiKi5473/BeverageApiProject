package tw.niels.beverage_api_project.modules.brand.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);

    List<Brand> findByIsActiveTrue();
}
