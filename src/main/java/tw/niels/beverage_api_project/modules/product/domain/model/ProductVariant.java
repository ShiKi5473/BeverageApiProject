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

    public ProductVariant(Long id, String name, Money price, boolean isActive) {
        this.id = id;
        this.name = name;
        this.price = price != null ? price : Money.ZERO;
        this.isActive = isActive;
    }

    public static ProductVariant create(String name, Money price) {
        return new ProductVariant(null, name, price, true);
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public Money getPrice() { return price; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
