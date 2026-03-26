package tw.niels.beverage_api_project.modules.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品 JPA 倉儲介面（重命名自 ProductRepository）
 * Layer: Infrastructure - Spring Data JPA Repository
 */
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @Query("""
        SELECT DISTINCT p
        FROM ProductEntity p
        LEFT JOIN FETCH p.categories
        LEFT JOIN FETCH p.variants
        LEFT JOIN FETCH p.optionGroups og
        LEFT JOIN FETCH og.options
        WHERE p.brand.id = :brandId AND p.status = :status
    """)
    List<ProductEntity> findByBrand_IdAndStatus(Long brandId, ProductStatus status);

    Optional<ProductEntity> findByBrand_IdAndId(Long brandId, Long id);

    Optional<ProductEntity> findByBrand_IdAndName(Long brandId, String name);

    List<ProductEntity> findByBrand_IdAndIdIn(Long brandId, Collection<Long> ids);

    // 保留所有原有方法
}
