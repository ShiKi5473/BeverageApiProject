package tw.niels.beverage_api_project.modules.member.domain.model;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Value Object
 * ============================================================
 * 
 * 會員點數值物件
 * 
 * 封裝點數數值與基本的異動邏輯。
 * 
 * 不可變（Immutable）：使用 Java record。
 * ============================================================
 */
public record MemberPoint(Long value) {
    public MemberPoint {
        if (value == null) value = 0L;
        if (value < 0) {
            throw new IllegalArgumentException("點數不得為負數");
        }
    }

    public static MemberPoint of(Long value) {
        return new MemberPoint(value);
    }

    public static MemberPoint zero() {
        return new MemberPoint(0L);
    }

    public MemberPoint add(Long amount) {
        return new MemberPoint(this.value + amount);
    }

    public MemberPoint subtract(Long amount) {
        return new MemberPoint(this.value - amount);
    }

    public boolean hasEnough(Long amount) {
        return this.value >= amount;
    }
}
