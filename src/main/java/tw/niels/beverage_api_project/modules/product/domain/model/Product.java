package tw.niels.beverage_api_project.modules.product.domain.model;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 商品聚合根
 *
 * 封裝商品的生命週期，管理相關的規格（Variants）、分類（Categories）
 * 與選項群組（OptionGroups）的關聯。
 *
 * 業務規則：
 * - 必須屬於一個品牌
 * - 擁有基礎售價（basePrice）
 * - 管理其規格、分類與選項群組的清單
 * ============================================================
 */
public class Product {

    private Long id;
    private final Long brandId;
    private String name;
    private String description;
    private Money basePrice;
    private String imageUrl;
    private boolean isActive;

    private List<ProductVariant> variants;
    private List<Long> categoryIds;
    private List<Long> optionGroupIds;

    public Product(Long id, Long brandId, String name, String description,
                   Money basePrice, String imageUrl, boolean isActive,
                   List<ProductVariant> variants, List<Long> categoryIds,
                   List<Long> optionGroupIds) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice != null ? basePrice : Money.ZERO;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
        this.variants = variants != null ? new ArrayList<>(variants) : new ArrayList<>();
        this.categoryIds = categoryIds != null ? new ArrayList<>(categoryIds) : new ArrayList<>();
        this.optionGroupIds = optionGroupIds != null ? new ArrayList<>(optionGroupIds) : new ArrayList<>();
    }

    public static Product create(Long brandId, String name, Money basePrice) {
        return new Product(null, brandId, name, null, basePrice, null, true,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getBasePrice() { return basePrice; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return isActive; }

    public List<ProductVariant> getVariants() { return Collections.unmodifiableList(variants); }
    public List<Long> getCategoryIds() { return Collections.unmodifiableList(categoryIds); }
    public List<Long> getOptionGroupIds() { return Collections.unmodifiableList(optionGroupIds); }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void addVariant(ProductVariant variant) {
        this.variants.add(variant);
    }

    public void assignCategory(Long categoryId) {
        if (!this.categoryIds.contains(categoryId)) {
            this.categoryIds.add(categoryId);
        }
    }

    public void linkOptionGroup(Long optionGroupId) {
        if (!this.optionGroupIds.contains(optionGroupId)) {
            this.optionGroupIds.add(optionGroupId);
        }
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
