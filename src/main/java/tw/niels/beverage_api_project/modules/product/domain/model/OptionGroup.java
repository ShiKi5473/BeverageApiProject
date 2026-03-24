package tw.niels.beverage_api_project.modules.product.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 選項群組聚合根
 *
 * 負責管理一組相關的選項（如：甜度、冰塊、加料），並遵循指定的選取規則（SelectionRule）。
 *
 * 業務規則：
 * - 必須包含選取規則（預設為自選多項）
 * - 管理內部的 ProductOption 實體清單
 * - 不變量由 SelectionRule 進行語意保護
 * ============================================================
 */
public class OptionGroup {

    private Long id;
    private final Long brandId;
    private String name;
    private SelectionRule selectionRule;
    private boolean isActive;

    private List<ProductOption> options;

    public OptionGroup(Long id, Long brandId, String name, SelectionRule selectionRule, boolean isActive, List<ProductOption> options) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.selectionRule = selectionRule != null ? selectionRule : SelectionRule.optionalMany();
        this.isActive = isActive;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public static OptionGroup create(Long brandId, String name, SelectionRule rule) {
        return new OptionGroup(null, brandId, name, rule, true, new ArrayList<>());
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public SelectionRule getSelectionRule() { return selectionRule; }
    public boolean isActive() { return isActive; }
    public List<ProductOption> getOptions() { return Collections.unmodifiableList(options); }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void addOption(ProductOption option) {
        this.options.add(option);
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
