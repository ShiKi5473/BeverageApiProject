package tw.niels.beverage_api_project.modules.product.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.product.domain.model.*;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.infrastructure.mapper.ProductMapper;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Product BC Domain Repository 的 Infrastructure 層實現
 * 管理 Product、Category、OptionGroup 三個 Aggregate 的持久化
 * ============================================================
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepo;
    private final CategoryJpaRepository categoryJpaRepo;
    private final OptionGroupJpaRepository optionGroupJpaRepo;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepo,
                                  CategoryJpaRepository categoryJpaRepo,
                                  OptionGroupJpaRepository optionGroupJpaRepo) {
        this.productJpaRepo = productJpaRepo;
        this.categoryJpaRepo = categoryJpaRepo;
        this.optionGroupJpaRepo = optionGroupJpaRepo;
    }

    // ─── Product 操作 ──────────────────────────────────────────────────────

    @Override
    public Optional<Product> findProductById(Long brandId, Long productId) {
        return productJpaRepo.findByBrand_IdAndId(brandId, productId)
            .map(ProductMapper::toDomain);
    }

    @Override
    public List<Product> findActiveProductsByBrand(Long brandId) {
        return productJpaRepo.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE)
            .stream()
            .map(ProductMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void saveProduct(Product product) {
        var jpaEntity = ProductMapper.toEntity(product);
        var saved = productJpaRepo.save(jpaEntity);
        if (product.getId() == null && saved.getId() != null) {
            try {
                var m = Product.class.getDeclaredMethod("setId", Long.class);
                m.setAccessible(true);
                m.invoke(product, saved.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步商品 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    // ─── Category 操作 ────────────────────────────────────────────────────

    @Override
    public Optional<Category> findCategoryById(Long brandId, Long categoryId) {
        return categoryJpaRepo.findByBrand_IdAndId(brandId, categoryId)
            .map(ProductMapper::toCategoryDomain);
    }

    @Override
    public List<Category> findActiveCategoriesByBrand(Long brandId) {
        return categoryJpaRepo.findByBrand_IdAndIsActive(brandId, true)
            .stream()
            .map(ProductMapper::toCategoryDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void saveCategory(Category category) {
        var jpaEntity = ProductMapper.toCategoryEntity(category);
        var saved = categoryJpaRepo.save(jpaEntity);
        if (category.getId() == null && saved.getId() != null) {
            try {
                var m = Category.class.getDeclaredMethod("setId", Long.class);
                m.setAccessible(true);
                m.invoke(category, saved.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步分類 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }

    // ─── OptionGroup 操作 ─────────────────────────────────────────────────

    @Override
    public Optional<OptionGroup> findOptionGroupById(Long brandId, Long groupId) {
        return optionGroupJpaRepo.findByBrand_IdAndId(brandId, groupId)
            .map(ProductMapper::toOptionGroupDomain);
    }

    @Override
    public List<OptionGroup> findOptionGroupsByBrand(Long brandId) {
        return optionGroupJpaRepo.findByBrand_Id(brandId)
            .stream()
            .map(ProductMapper::toOptionGroupDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void saveOptionGroup(OptionGroup optionGroup) {
        var jpaEntity = ProductMapper.toOptionGroupEntity(optionGroup);
        var saved = optionGroupJpaRepo.save(jpaEntity);
        if (optionGroup.getId() == null && saved.getId() != null) {
            try {
                var m = OptionGroup.class.getDeclaredMethod("setId", Long.class);
                m.setAccessible(true);
                m.invoke(optionGroup, saved.getId());
            } catch (Exception e) {
                System.err.println("警告：無法同步選項群組 ID 回 Domain Model：" + e.getMessage());
            }
        }
    }
}
