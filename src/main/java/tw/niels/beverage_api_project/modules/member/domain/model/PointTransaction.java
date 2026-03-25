package tw.niels.beverage_api_project.modules.member.domain.model;

import java.time.Instant;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Entity（屬於 Member Aggregate）
 * ============================================================
 *
 * 積點交易紀錄實體（Domain Entity）
 *
 * 代表會員積點帳戶的每一筆異動記錄（賺取、使用、退還、過期、人工調整）。
 * 作為不可變的稽核日誌，一旦建立後不允許修改任何欄位。
 *
 * 業務規則：
 * - amount 不得為 0（零分異動無意義）
 * - amount 正數 = 增加積點；負數 = 扣減積點
 * - 由 Member Aggregate 的業務方法（earnPoints / consumePoints）觸發建立
 *
 * 不變量（Invariants）：
 * - amount ≠ 0
 * - createdAt 為建立時間，不可修改
 *
 * 生命週期：
 * - 隸屬於 Member Aggregate，不可脫離 Member 單獨存在
 * ============================================================
 */
public class PointTransaction {

    /** 交易紀錄 ID（TSID），由 Infrastructure Layer 在持久化後設定 */
    private Long id;

    /** 所屬會員 ID */
    private final Long memberId;

    /**
     * 積點異動數量
     * - 正數：增加積點（累積、退還）
     * - 負數：扣減積點（折抵、過期）
     */
    private final Long amount;

    /** 交易類型（說明此筆異動的業務原因） */
    private final PointTransactionType type;

    /**
     * 異動原因說明，通常包含來源業務單號。
     * 範例：「訂單 #A001 結帳累積」、「人工補點：活動補償」
     */
    private final String reason;

    /** 交易發生時間（建立時自動設定，不可修改） */
    private final Instant createdAt;

    /**
     * 私有建構子：強制使用靜態工廠方法建立，確保 createdAt 由 Domain 管理。
     */
    private PointTransaction(Long memberId, Long amount,
                             PointTransactionType type, String reason) {
        if (amount == null || amount == 0) {
            throw new IllegalArgumentException("積點交易金額不得為 0");
        }
        this.memberId = memberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    /**
     * 建立一筆積點交易紀錄。
     *
     * @param memberId 所屬會員 ID
     * @param amount   異動數量（正數增加、負數扣減，不得為 0）
     * @param type     交易類型
     * @param reason   原因說明（如訂單編號、活動名稱）
     * @return 新的 PointTransaction 實例
     */
    public static PointTransaction of(Long memberId, Long amount,
                                      PointTransactionType type, String reason) {
        return new PointTransaction(memberId, amount, type, reason);
    }

    // ─── Getters（無 setters，確保不可變稽核性）────────────────────────────

    /**
     * 由 Infrastructure Mapper 在持久化後回填 ID。
     * package-private：防止外部任意修改不可變稽核紀錄的 ID。
     */
    void setId(Long id) { this.id = id; }

    public Long getId()        { return id; }
    public Long getMemberId()  { return memberId; }
    public Long getAmount()    { return amount; }
    public PointTransactionType getType() { return type; }
    public String getReason()  { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
