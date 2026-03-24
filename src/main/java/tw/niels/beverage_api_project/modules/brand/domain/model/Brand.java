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

    private final Long id;
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
}
