package tw.niels.beverage_api_project.modules.member.domain.model;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 會員等級值物件
 *
 * 定義會員的等級制度（如：一般、黃金、白金）。
 * 未來可用於決定不同的點數累積率或專屬折扣。
 * ============================================================
 */
public record MemberLevel(String code, String name) {
    
    public static MemberLevel regular() {
        return new MemberLevel("REGULAR", "一般會員");
    }

    public static MemberLevel gold() {
        return new MemberLevel("GOLD", "黃金會員");
    }
}
