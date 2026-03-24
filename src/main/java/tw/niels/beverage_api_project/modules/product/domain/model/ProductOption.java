package tw.niels.beverage_api_project.modules.product.domain.model;

import tw.niels.beverage_api_project.common.domain.vo.Money;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Entity（屬於 OptionGroup Aggregate）
 * ============================================================
 *
 * 商品選項實體
 *
 * 代表選項群組中的單一可選項目，例如「去冰」、「半糖」、「加珍珠（+10 元）」。
 *
 * 不變量（Invariants）：
 * -額外費用（additionalPrice）不得為負數（已有 Money VO 保證）
 * ============================================================
 */
public class ProductOption {

    /** 選項 ID（TSID） */
    private Long id;

    /** 選項名稱（如：去冰、珍珠） */
    private String name;

    /** 此選項的加價金額（0 代表不加價） */
    private Money additionalPrice;

    /** 是否預設勾選 */
    private boolean isDefault;

    /** 是否啟用中 */
    private boolean isActive;

    public ProductOption(Long id, String name, Money additionalPrice, boolean isDefault, boolean isActive) {
        this.id = id;
        this.name = name;
        this.additionalPrice = additionalPrice != null ? additionalPrice : Money.ZERO;
        this.isDefault = isDefault;
        this.isActive = isActive;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public Money getAdditionalPrice() { return additionalPrice; }
    public boolean isDefault() { return isDefault; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
