package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByBrand_BrandIdAndStatus(Long brandId, ProductStatus Status);

    Optional<Product> findByBrand_BrandIdAndProductId(Long brandId, Long productId);
}
