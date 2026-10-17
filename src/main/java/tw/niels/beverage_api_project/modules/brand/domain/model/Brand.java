package tw.niels.beverage_api_project.modules.brand.domain.model;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 品牌聚合根
 *
 * 管理品牌的基本資訊與全域積點配置。
 * ============================================================
 */
public class Brand {

    // 非 final：新增品牌時 id 為 null，持久化後由 Infrastructure 透過 assignId() 回填（取代原本的反射 setId）
    private Long id;
    private String name;
    private String contactPerson;
    private BrandPointConfig pointConfig;
    private boolean isActive;

    public Brand(Long id, String name, String contactPerson, BrandPointConfig pointConfig, boolean isActive) {
        this.id = id;
        this.name = name;
        this.contactPerson = contactPerson;
        this.pointConfig = pointConfig != null ? pointConfig : BrandPointConfig.defaultSettings();
        this.isActive = isActive;
    }

    public static Brand create(String name) {
        return new Brand(null, name, null, BrandPointConfig.defaultSettings(), true);
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public BrandPointConfig getPointConfig() { return pointConfig; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void updateInfo(String name, String contactPerson) {
        this.name = name;
        this.contactPerson = contactPerson;
    }

    public void updatePointConfig(BrandPointConfig config) {
        this.pointConfig = config;
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    /**
     * 首次持久化後回填資料庫端產生的 ID（由 BrandRepositoryImpl 呼叫）。
     * 清理(P3)：取代原本 BrandRepositoryImpl 用反射呼叫根本不存在的 private setId（靜默失敗，ID 永遠回填不了）。
     * 只允許在 id 尚為 null 時設定一次，避免已持久化的聚合被改動識別碼。
     */
    public void assignId(Long newId) {
        if (this.id != null) {
            throw new IllegalStateException("品牌 ID 已存在，不可重新指派");
        }
        this.id = newId;
    }
}
