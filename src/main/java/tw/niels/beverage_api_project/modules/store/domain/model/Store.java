package tw.niels.beverage_api_project.modules.store.domain.model;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 門市聚合根
 *
 * 管理門市的基本資訊（名稱、地址、電話）與其所屬品牌。
 * ============================================================
 */
public class Store {

    private final Long id;
    private final Long brandId;
    private String name;
    private String address;
    private String phoneNumber;
    private boolean isActive;

    public Store(Long id, Long brandId, String name, String address, String phoneNumber, boolean isActive) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.isActive = isActive;
    }

    public static Store create(Long brandId, String name) {
        return new Store(null, brandId, name, null, null, true);
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhoneNumber() { return phoneNumber; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    public void updateDetails(String name, String address, String phoneNumber) {
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
