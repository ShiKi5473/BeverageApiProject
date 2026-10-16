package tw.niels.beverage_api_project.modules.product.domain.model;

import tw.niels.beverage_api_project.common.domain.vo.Money;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Entity（屬於 Product Aggregate）
 * ============================================================
 *
 * 商品規格實體
 * 
 * 代表商品的具體 SKU，例如「大杯」、「中杯」或「熱」、「冰」。
 * 不同規格可能有不同的售價。
 *
 * 不變量（Invariants）：
 * - 規格名稱不得為空
 * - 售價（price）不得為負數
 * ============================================================
 */
public class ProductVariant {

    /** 規格 ID（TSID） */
    private Long id;

    /** 規格名稱（如：大杯、熱） */
    private String name;

    /** 此規格的售價（不含選項加價） */
    private Money price;

    /** 是否啟用中 */
    private boolean isActive;

    /** SKU 代碼（選填，用於庫存識別） */
    private String skuCode;

    public ProductVariant(Long id, String name, Money price, boolean isActive, String skuCode) {
        this.id = id;
        this.name = name;
        this.price = price != null ? price : Money.ZERO;
        this.isActive = isActive;
        this.skuCode = skuCode;
    }

    /**
     * 建立新規格的工廠方法。
     *
     * @param name    規格名稱（如：大杯、熱）
     * @param price   規格售價
     * @param skuCode SKU 代碼（選填）
     * @return 新建的規格實體（isActive = true）
     */
    public static ProductVariant create(String name, Money price, String skuCode) {
        return new ProductVariant(null, name, price, true, skuCode);
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public Money getPrice() { return price; }
    public boolean isActive() { return isActive; }
    public String getSkuCode() { return skuCode; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    /**
     * 更新規格屬性（名稱、售價、SKU 代碼）。
     * 僅更新非 null 的參數，null 表示不變更該欄位。
     *
     * @param name    新規格名稱（null 不更新）
     * @param price   新售價（null 不更新）
     * @param skuCode 新 SKU 代碼（null 不更新）
     */
    public void update(String name, Money price, String skuCode) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
        if (skuCode != null) {
            this.skuCode = skuCode;
        }
    }
}
