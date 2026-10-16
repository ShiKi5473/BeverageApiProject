package tw.niels.beverage_api_project.modules.product.infrastructure.mapper;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.product.domain.model.*;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;
import tw.niels.beverage_api_project.modules.product.entity.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Infrastructure - Mapper
 * ============================================================
 *
 * Product BC - JPA Entity ↔ Domain Model 雙向映射工具
 *
 * 修改說明（P0-1 聚合更新必失敗）：
 * 原本的 toEntity 每次都 {@code new XxxEntity()} 再 setId(既有 ID)，
 * 因 BaseTsidEntity.isNew() 對新物件恆為 true，save() 會走 persist()，
 * 而同一交易已載入相同 ID 的受管實體 → NonUniqueObjectException。
 * 現改為「把 Domain 狀態套用到受管實體上」（apply 系列方法），
 * 只有真正新建的子實體（新規格、新選項）才 new 出來；
 * 關聯（品牌、分類、選項群組）由 RepositoryImpl 以 EntityManager／品牌隔離查詢取得，
 * Mapper 不再自行 new 出只有 ID 的關聯物件。
 * ============================================================
 */
public final class ProductMapper {

    private ProductMapper() {
    }

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

    /**
     * 將 Domain 商品的純量欄位套用到（受管或新建的）ProductEntity。
     * 新增：取代舊 toEntity 的純量欄位複製邏輯。
     */
    public static void applyProductScalars(ProductEntity target, Product source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setImageUrl(source.getImageUrl());
        target.setBasePrice(source.getBasePrice() != null ? source.getBasePrice().amount() : null);

        // Domain 只有 isActive 布林值：啟用 → ACTIVE；
        // 停用時若原本是 ACTIVE（或尚未設定）才改成 INACTIVE，保留既有的 DISCONTINUED 狀態不被覆蓋
        if (source.isActive()) {
            target.setStatus(ProductStatus.ACTIVE);
        } else if (target.getStatus() == null || target.getStatus() == ProductStatus.ACTIVE) {
            target.setStatus(ProductStatus.INACTIVE);
        }
    }

    /**
     * 依規格 ID 將 Domain 規格同步到受管 ProductEntity 的規格集合。
     * 新增（P0-1）：
     * - ID 相符 → 更新名稱、價格、SKU、刪除旗標（isActive=false → isDeleted=true，軟刪除）
     * - 沒有 ID → 視為新規格，new 一個 ProductVariantEntity 並建立雙向關聯（cascade 持久化）
     * - 實體有、Domain 沒有 → 軟刪除（絕不實體刪除，因訂單品項／配方仍引用規格）
     */
    public static void syncVariants(ProductEntity target, List<ProductVariant> source) {
        if (target.getVariants() == null) {
            target.setVariants(new HashSet<>());
        }

        Map<Long, ProductVariantEntity> existing = new HashMap<>();
        for (ProductVariantEntity v : target.getVariants()) {
            existing.put(v.getId(), v);
        }

        Set<Long> seen = new HashSet<>();
        if (source != null) {
            for (ProductVariant domainVariant : source) {
                Long id = domainVariant.getId();
                if (id == null) {
                    // 新規格：建立新實體（建構時自動產生 TSID，isNew=true → cascade persist）
                    target.addVariant(newVariantEntity(domainVariant));
                } else if (existing.containsKey(id)) {
                    applyVariant(existing.get(id), domainVariant);
                    seen.add(id);
                } else {
                    // Domain 帶著不屬於此商品的規格 ID，代表呼叫端資料錯亂，直接中止避免寫入錯誤資料
                    throw new IllegalStateException("規格 ID " + id + " 不屬於商品 " + target.getId());
                }
            }
        }

        // Domain 已不包含的既有規格一律軟刪除
        existing.forEach((id, entity) -> {
            if (!seen.contains(id)) {
                entity.setDeleted(true);
            }
        });
    }

    // ─── ProductVariant 映射 ───────────────────────────────────────────────

    // 補齊 skuCode 欄位映射，避免經由 Domain Model 保存時遺失資料
    public static ProductVariant toVariantDomain(ProductVariantEntity jpa) {
        if (jpa == null) return null;
        return new ProductVariant(
            jpa.getId(),
            jpa.getName(),
            jpa.getPrice() != null ? Money.of(jpa.getPrice()) : Money.ZERO,
            !jpa.isDeleted(),
            jpa.getSkuCode()
        );
    }

    /** 新增：將 Domain 規格狀態套用到既有（受管）規格實體 */
    public static void applyVariant(ProductVariantEntity target, ProductVariant source) {
        target.setName(source.getName());
        target.setPrice(source.getPrice() != null ? source.getPrice().amount() : null);
        target.setSkuCode(source.getSkuCode());
        target.setDeleted(!source.isActive());
    }

    /** 新增：為「尚未持久化」的 Domain 規格建立新實體（ID 由 BaseTsidEntity 建構時產生） */
    public static ProductVariantEntity newVariantEntity(ProductVariant source) {
        var jpa = new ProductVariantEntity();
        applyVariant(jpa, source);
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

    /** 新增：將 Domain 分類狀態套用到（受管或新建的）分類實體；品牌關聯由 RepositoryImpl 設定 */
    public static void applyCategory(CategoryEntity target, Category source) {
        target.setName(source.getName());
        target.setSortOrder(source.getDisplayOrder());
        target.setActive(source.isActive());
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

    /**
     * 新增：將 Domain 選項群組狀態套用到（受管或新建的）群組實體，並依 ID 同步選項。
     * 舊版 toOptionGroupEntity 完全沒有映射 options，新建群組時選項會遺失。
     * 選項表沒有軟刪除欄位，Domain 未包含的既有選項保持不動（避免實體刪除被配方／訂單引用的選項）。
     * sortOrder 不在 Domain Model 中，保留實體原值。
     */
    public static void applyOptionGroup(OptionGroupEntity target, OptionGroup source) {
        target.setName(source.getName());
        target.setSelectionType(
            source.getSelectionRule() != null && source.getSelectionRule().minSelection() > 0
                ? SelectionType.SINGLE
                : SelectionType.MULTIPLE
        );

        if (target.getOptions() == null) {
            target.setOptions(new HashSet<>());
        }
        Map<Long, ProductOptionEntity> existing = new HashMap<>();
        for (ProductOptionEntity o : target.getOptions()) {
            existing.put(o.getId(), o);
        }

        for (ProductOption domainOption : source.getOptions()) {
            Long id = domainOption.getId();
            if (id == null) {
                ProductOptionEntity newOption = new ProductOptionEntity();
                applyOption(newOption, domainOption);
                newOption.setOptionGroup(target);
                target.getOptions().add(newOption);
            } else if (existing.containsKey(id)) {
                applyOption(existing.get(id), domainOption);
            } else {
                throw new IllegalStateException("選項 ID " + id + " 不屬於選項群組 " + target.getId());
            }
        }
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

    /** 新增：將 Domain 選項狀態套用到選項實體（取代舊 toOptionEntity 的 new + setId 寫法） */
    public static void applyOption(ProductOptionEntity target, ProductOption source) {
        target.setOptionName(source.getName());
        target.setPriceAdjustment(source.getAdditionalPrice() != null
            ? source.getAdditionalPrice().amount()
            : java.math.BigDecimal.ZERO); // price_adjustment 為 NOT NULL，缺值以 0 代替
        target.setDefault(source.isDefault());
    }
}
