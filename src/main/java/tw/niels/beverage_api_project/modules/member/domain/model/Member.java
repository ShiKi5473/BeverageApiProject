package tw.niels.beverage_api_project.modules.member.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 會員聚合根
 *
 * 合併 User（會員部分）、MemberProfile 與積點管理為單一 Aggregate。
 * 所有積點異動必須透過業務方法觸發，並自動產生對應的 PointTransaction 稽核紀錄。
 *
 * 業務規則：
 * - 積點異動必須透過業務方法（earnPoints, consumePoints），不得直接修改餘額
 * - 積點餘額永遠不得為負數（由 MemberPoint VO 保證）
 * - 每次積點異動都會產生一筆 PointTransaction（稽核日誌）
 * - 停用的會員（isActive = false）不允許新增積點
 *
 * 不變量（Invariants）：
 * - points.value() ≥ 0（由 MemberPoint VO 保護）
 *
 * 發佈事件：
 * - 目前由 Application Layer 直接發佈，Phase 5 補充 Domain Event
 * ============================================================
 */
public class Member {

    /** 會員 ID（與 User 表的 ID 相同，跨 BC 識別鍵） */
    private final Long id;

    /** 所屬品牌 ID（多租戶隔離） */
    private final Long brandId;

    /**
     * 會員個人資料（Value Object）
     * 包含姓名、電話、Email、生日、性別
     */
    private MemberProfile profile;

    /** 積點餘額（Value Object，確保非負） */
    private MemberPoint points;

    /** 會員等級（Value Object） */
    private MemberLevel level;

    /** 是否啟用 */
    private boolean isActive;

    /**
     * 積點交易歷史（Entity 清單，屬於此 Aggregate）
     * 由 earnPoints / consumePoints 方法自動新增，不允許外部直接操作
     */
    private final List<PointTransaction> pointTransactions = new ArrayList<>();

    // ─── 建構子（供 Domain 工廠方法與 Infrastructure Mapper 使用）───────────

    /**
     * 建立新會員（工廠方法使用）。
     */
    public Member(Long id, Long brandId, MemberProfile profile,
                  MemberPoint points, MemberLevel level, boolean isActive) {
        this.id = id;
        this.brandId = brandId;
        this.profile = profile;
        this.points = points != null ? points : MemberPoint.zero();
        this.level = level != null ? level : MemberLevel.regular();
        this.isActive = isActive;
    }

    // ─── 靜態工廠方法 ──────────────────────────────────────────────────────

    /**
     * 建立一位新會員（首次加入品牌會員計劃）。
     *
     * @param id       會員 ID（與 User 相同）
     * @param brandId  品牌 ID
     * @param profile  個人資料（姓名、電話、Email 等）
     * @return 積點為 0、等級為 REGULAR 的新會員
     */
    public static Member create(Long id, Long brandId, MemberProfile profile) {
        return new Member(id, brandId, profile, MemberPoint.zero(), MemberLevel.regular(), true);
    }

    // ─── 業務方法 ──────────────────────────────────────────────────────────

    /**
     * 累積積點（訂單完成後由 Application Layer 呼叫）。
     *
     * @param amount 累積數量（必須 > 0）
     * @param reason 原因說明（如：「訂單 #A001 結帳累積」）
     * @throws IllegalStateException 若會員已停用
     */
    public void earnPoints(Long amount, String reason) {
        if (!this.isActive) {
            throw new IllegalStateException("停用的會員無法累積積點");
        }
        if (amount != null && amount > 0) {
            this.points = this.points.add(amount);
            pointTransactions.add(
                    PointTransaction.of(this.id, amount, PointTransactionType.EARN_ORDER, reason)
            );
        }
    }

    /**
     * 折抵使用積點（POS 結帳時呼叫）。
     *
     * @param amount 使用數量（必須 > 0）
     * @param reason 原因說明（如：「訂單 #A001 積點折抵」）
     * @throws IllegalStateException 若積點不足
     */
    public void consumePoints(Long amount, String reason) {
        if (!this.points.hasEnough(amount)) {
            throw new IllegalStateException(
                    "會員積點不足，現有 " + this.points.value() + " 點，需要 " + amount + " 點"
            );
        }
        this.points = this.points.subtract(amount);
        pointTransactions.add(
                PointTransaction.of(this.id, -amount, PointTransactionType.REDEEM_ORDER, reason)
        );
    }

    /**
     * 退還積點（訂單取消時呼叫）。
     *
     * @param amount 退還數量（必須 > 0）
     * @param reason 原因說明（如：「訂單 #A001 取消退還」）
     */
    public void refundPoints(Long amount, String reason) {
        if (amount != null && amount > 0) {
            this.points = this.points.add(amount);
            pointTransactions.add(
                    PointTransaction.of(this.id, amount, PointTransactionType.REFUND_CANCEL, reason)
            );
        }
    }

    /**
     * 更新個人資料。
     *
     * @param newName  新姓名
     * @param newEmail 新 Email
     */
    public void updateProfile(String newName, String newEmail) {
        this.profile = this.profile.withNameAndEmail(newName, newEmail);
    }

    /**
     * 人工調整積點（管理員手動補點或扣點）。
     *
     * 允許正數（增加積點）或負數（扣減積點）的調整。
     * 零調整視為無效操作，直接忽略。
     * 調整後餘額不得為負數（由 MemberPoint VO 的 subtract() 方法保護）。
     *
     * @param amount 調整數量（正數增加，負數扣減）；為 0 時略過
     * @param reason 原因說明（如：「活動補償」、「誤點更正」）
     * @throws IllegalStateException 若扣減後餘額不足
     */
    public void adjustPoints(Long amount, String reason) {
        if (amount == null || amount == 0) return;
        if (amount < 0 && !this.points.hasEnough(-amount)) {
            throw new IllegalStateException(
                "積點不足，無法扣減：現有 " + this.points.value() + " 點，嘗試扣減 " + (-amount) + " 點"
            );
        }
        this.points = amount > 0 ? this.points.add(amount) : this.points.subtract(-amount);
        pointTransactions.add(
            PointTransaction.of(this.id, amount, PointTransactionType.ADMIN_ADJUST, reason)
        );
    }

    /**
     * 停用此會員。
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 取出新增的積點交易紀錄（由 Application Layer 在儲存後取出並持久化）。
     *
     * @return 新增的 PointTransaction 清單（不可修改的副本，原清單被清空）
     */
    public List<PointTransaction> pullPointTransactions() {
        List<PointTransaction> result = List.copyOf(this.pointTransactions);
        this.pointTransactions.clear();
        return result;
    }

    // ─── Getters（無 public setters，保護 Aggregate 完整性）──────────────────

    public Long getId()              { return id; }
    public Long getBrandId()         { return brandId; }
    public MemberProfile getProfile(){ return profile; }
    public MemberPoint getPoints()   { return points; }
    public MemberLevel getLevel()    { return level; }
    public boolean isActive()        { return isActive; }

    /** 回傳不可修改的積點交易清單（已持久化的歷史紀錄） */
    public List<PointTransaction> getPointTransactions() {
        return Collections.unmodifiableList(pointTransactions);
    }
}
