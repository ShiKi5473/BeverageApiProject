package tw.niels.beverage_api_project.modules.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByBrand_BrandIdAndIsAvailable(Long brandId, boolean isAvailable);

    List<ProductSummaryDto> findSummaryDtoByBrand_BrandIdAndIsAvailable(Long brandId, boolean isAvailable);

    List<ProductPosDto> findPosDtoByBrand_BrandIdAndIsAvailable(Long brandId, boolean isAvailable);

    Optional<Product> findByBrand_BrandIdAndProductId(Long brandId, Long productId);
}
