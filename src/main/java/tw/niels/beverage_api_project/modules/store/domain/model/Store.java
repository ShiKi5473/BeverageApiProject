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

    // 非 final：新增門市時 id 為 null，持久化後由 Infrastructure 透過 assignId() 回填（取代原本的反射 setId）
    private Long id;
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

    /**
     * 首次持久化後回填資料庫端產生的 ID（由 StoreRepositoryImpl 呼叫）。
     * 清理(P3)：取代原本 StoreRepositoryImpl 用反射呼叫根本不存在的 private setId（靜默失敗，ID 永遠回填不了）。
     * 只允許在 id 尚為 null 時設定一次，避免已持久化的聚合被改動識別碼。
     */
    public void assignId(Long newId) {
        if (this.id != null) {
            throw new IllegalStateException("門市 ID 已存在，不可重新指派");
        }
        this.id = newId;
    }
}
