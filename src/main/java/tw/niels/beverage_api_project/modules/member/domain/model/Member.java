package tw.niels.beverage_api_project.modules.member.domain.model;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 會員聚合根
 *
 * 負責管理會員的基底資訊、點數餘額與等級。
 *
 * 業務規則：
 * - 點數異動必須透過業務方法（earnPoints, consumePoints）。
 * - 點數餘額永遠不得為負數（由 MemberPoint VO 保證）。
 * ============================================================
 */
public class Member {

    private final Long id; // 與 User ID 相同
    private final Long brandId;
    private String fullName;
    private String phone;
    private String email;
    private MemberPoint points;
    private MemberLevel level;
    private boolean isActive;

    public Member(Long id, Long brandId, String fullName, String phone, String email,
                  MemberPoint points, MemberLevel level, boolean isActive) {
        this.id = id;
        this.brandId = brandId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.points = points != null ? points : MemberPoint.zero();
        this.level = level != null ? level : MemberLevel.regular();
        this.isActive = isActive;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getBrandId() { return brandId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public MemberPoint getPoints() { return points; }
    public MemberLevel getLevel() { return level; }
    public boolean isActive() { return isActive; }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    /**
     * 累積點數。
     */
    public void earnPoints(Long amount) {
        if (amount > 0) {
            this.points = this.points.add(amount);
        }
    }

    /**
     * 消費/扣除點數。
     */
    public void consumePoints(Long amount) {
        if (!this.points.hasEnough(amount)) {
            throw new IllegalStateException("會員點數不足，無法扣除");
        }
        this.points = this.points.subtract(amount);
    }

    public void updateProfile(String name, String email) {
        this.fullName = name;
        this.email = email;
    }

    public void deactivate() { this.isActive = false; }
}
