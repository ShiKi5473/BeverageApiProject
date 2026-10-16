package tw.niels.beverage_api_project.modules.product.domain.model;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 商品分類聚合根
 *
 * 管理商品分類的名稱、排序與啟用狀態。
 * 分類之間相對獨立，通常作為 Product 的標籤使用。
 * ============================================================
 */
public class Category {

    private Long id;
    private final Long brandId;
    private String name;
    private int displayOrder;
    private boolean isActive;

    public Category(Long id, Long brandId, String name, int displayOrder, boolean isActive) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
    }

    public static Category create(Long brandId, String name, int order) {
        return new Category(null, brandId, name, order, true);
    }

    /**
     * 由 Infrastructure Layer 在持久化後回填 ID。
     * 修改：取代 package-private setId（原本需透過反射呼叫），只允許「尚未有 ID」時指派。
     */
    public void assignId(Long id) {
        if (this.id != null && !this.id.equals(id)) {
            throw new IllegalStateException("分類已有 ID，不可重新指派：" + this.id);
        }
        this.id = id;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
    public void updateInfo(String name, int order) {
        this.name = name;
        this.displayOrder = order;
    }
}
