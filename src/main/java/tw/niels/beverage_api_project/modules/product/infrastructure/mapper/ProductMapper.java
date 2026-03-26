package tw.niels.beverage_api_project.modules.product.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.product.domain.model.*;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;
import tw.niels.beverage_api_project.modules.product.entity.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Product BC - JPA Entity ↔ Domain Model 雙向映射工具
 * ============================================================
 */
public class ProductMapper {

    // ─── Product 映射 ──────────────────────────────────────────────────────

    public static Product toDomain(ProductEntity jpa) {
        if (jpa == null) return null;

        List<ProductVariant> variants = jpa.getVariants() != null
            ? jpa.getVariants().stream()
                .map(ProductMapper::toVariantDomain)
                .collect(Collectors.toList())
            : Collections.emptyList();

        List<Long> categoryIds = jpa.getCategories() != null
            ? jpa.getCategories().stream()
                .map(CategoryEntity::getId)
                .collect(Collectors.toList())
            : Collections.emptyList();

        List<Long> optionGroupIds = jpa.getOptionGroups() != null
            ? jpa.getOptionGroups().stream()
                .map(OptionGroupEntity::getId)
                .collect(Collectors.toList())
            : Collections.emptyList();

        return new Product(
            jpa.getId(),
            jpa.getBrand() != null ? jpa.getBrand().getId() : null,
            jpa.getName(),
            jpa.getDescription(),
            jpa.getBasePrice() != null ? Money.of(jpa.getBasePrice()) : Money.ZERO,
            jpa.getImageUrl(),
            jpa.getStatus() == ProductStatus.ACTIVE,
            variants,
            categoryIds,
            optionGroupIds
        );
    }

    public static ProductEntity toEntity(Product domain) {
        if (domain == null) return null;

        var jpa = new ProductEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());

        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpa.setBrand(brand);
        }

        jpa.setName(domain.getName());
        jpa.setDescription(domain.getDescription());
        jpa.setImageUrl(domain.getImageUrl());
        jpa.setBasePrice(domain.getBasePrice() != null ? domain.getBasePrice().amount() : null);
        jpa.setStatus(domain.isActive() ? ProductStatus.ACTIVE : ProductStatus.INACTIVE);

        if (domain.getVariants() != null && !domain.getVariants().isEmpty()) {
            var jpaVariants = domain.getVariants().stream()
                .map(v -> {
                    var jpaVariant = toVariantEntity(v);
                    jpaVariant.setProduct(jpa);
                    return jpaVariant;
                })
                .collect(Collectors.toSet());
            jpa.setVariants(jpaVariants);
        }

        if (domain.getCategoryIds() != null && !domain.getCategoryIds().isEmpty()) {
            var categoryProxies = domain.getCategoryIds().stream()
                .map(catId -> {
                    var cat = new CategoryEntity();
                    cat.setId(catId);
                    return cat;
                })
                .collect(Collectors.toSet());
            jpa.setCategories(categoryProxies);
        }

        if (domain.getOptionGroupIds() != null && !domain.getOptionGroupIds().isEmpty()) {
            var ogProxies = domain.getOptionGroupIds().stream()
                .map(ogId -> {
                    var og = new OptionGroupEntity();
                    og.setId(ogId);
                    return og;
                })
                .collect(Collectors.toSet());
            jpa.setOptionGroups(ogProxies);
        }

        return jpa;
    }

    // ─── ProductVariant 映射 ───────────────────────────────────────────────

    public static ProductVariant toVariantDomain(ProductVariantEntity jpa) {
        if (jpa == null) return null;
        return new ProductVariant(
            jpa.getId(),
            jpa.getName(),
            jpa.getPrice() != null ? Money.of(jpa.getPrice()) : Money.ZERO,
            !jpa.isDeleted()
        );
    }

    public static ProductVariantEntity toVariantEntity(ProductVariant domain) {
        if (domain == null) return null;
        var jpa = new ProductVariantEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());
        jpa.setName(domain.getName());
        jpa.setPrice(domain.getPrice() != null ? domain.getPrice().amount() : null);
        jpa.setDeleted(!domain.isActive());
        return jpa;
    }

    // ─── Category 映射 ────────────────────────────────────────────────────

    public static Category toCategoryDomain(CategoryEntity jpa) {
        if (jpa == null) return null;
        return new Category(
            jpa.getId(),
            jpa.getBrand() != null ? jpa.getBrand().getId() : null,
            jpa.getName(),
            jpa.getSortOrder() != null ? jpa.getSortOrder() : 0,
            jpa.isActive()
        );
    }

    public static CategoryEntity toCategoryEntity(Category domain) {
        if (domain == null) return null;
        var jpa = new CategoryEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());
        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpa.setBrand(brand);
        }
        jpa.setName(domain.getName());
        jpa.setSortOrder(domain.getDisplayOrder());
        jpa.setActive(domain.isActive());
        return jpa;
    }

    // ─── OptionGroup 映射 ─────────────────────────────────────────────────

    public static OptionGroup toOptionGroupDomain(OptionGroupEntity jpa) {
        if (jpa == null) return null;

        SelectionRule selectionRule = jpa.getSelectionType() == SelectionType.SINGLE
            ? SelectionRule.requiredOne()
            : SelectionRule.optionalMany();

        List<ProductOption> options = jpa.getOptions() != null
            ? jpa.getOptions().stream()
                .map(ProductMapper::toOptionDomain)
                .collect(Collectors.toList())
            : Collections.emptyList();

        return new OptionGroup(
            jpa.getId(),
            jpa.getBrand() != null ? jpa.getBrand().getId() : null,
            jpa.getName(),
            selectionRule,
            true,
            options
        );
    }

    public static OptionGroupEntity toOptionGroupEntity(OptionGroup domain) {
        if (domain == null) return null;
        var jpa = new OptionGroupEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());
        if (domain.getBrandId() != null) {
            var brand = new tw.niels.beverage_api_project.modules.brand.entity.BrandEntity();
            brand.setId(domain.getBrandId());
            jpa.setBrand(brand);
        }
        jpa.setName(domain.getName());
        jpa.setSelectionType(
            domain.getSelectionRule() != null && domain.getSelectionRule().minSelection() > 0
                ? SelectionType.SINGLE
                : SelectionType.MULTIPLE
        );
        return jpa;
    }

    // ─── ProductOption 映射 ───────────────────────────────────────────────

    public static ProductOption toOptionDomain(ProductOptionEntity jpa) {
        if (jpa == null) return null;
        return new ProductOption(
            jpa.getId(),
            jpa.getOptionName(),
            jpa.getPriceAdjustment() != null
                ? Money.of(jpa.getPriceAdjustment())
                : Money.ZERO,
            jpa.isDefault(),
            true
        );
    }

    public static ProductOptionEntity toOptionEntity(ProductOption domain) {
        if (domain == null) return null;
        var jpa = new ProductOptionEntity();
        if (domain.getId() != null) jpa.setId(domain.getId());
        jpa.setOptionName(domain.getName());
        jpa.setPriceAdjustment(domain.getAdditionalPrice() != null
            ? domain.getAdditionalPrice().amount()
            : null);
        jpa.setDefault(domain.isDefault());
        return jpa;
    }
}
