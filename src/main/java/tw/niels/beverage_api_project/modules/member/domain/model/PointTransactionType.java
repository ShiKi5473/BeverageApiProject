package tw.niels.beverage_api_project.modules.member.domain.model;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Value Object（Enum）
 * ============================================================
 *
 * 積點交易類型列舉
 *
 * 描述每一筆 PointTransaction（積點歷史紀錄）的觸發原因，
 * 方便後台查詢、報表分析與爭議處理。
 * ============================================================
 */
public enum PointTransactionType {

    /**
     * 消費累積：顧客完成訂單（CLOSED）後，依品牌積點規則累積的點數。
     */
    EARN_ORDER,

    /**
     * 折抵使用：POS 結帳時顧客主動使用點數折抵金額。
     */
    REDEEM_ORDER,

    /**
     * 取消退還：因訂單取消（CANCELLED），退回先前扣減的點數。
     */
    REFUND_CANCEL,

    /**
     * 過期扣除：系統依品牌規則定期清除已到期的積點。
     */
    EXPIRE,

    /**
     * 人工調整：品牌管理員手動新增或扣除點數（含補償、修正）。
     */
    ADMIN_ADJUST
}
