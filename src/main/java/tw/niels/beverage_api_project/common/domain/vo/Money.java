package tw.niels.beverage_api_project.common.domain.vo;

import tw.niels.beverage_api_project.common.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ============================================================
 * Bounded Context: Common
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 金額值物件（Value Object）
 *
 * 封裝 BigDecimal，防止直接使用 primitive 造成的精度問題，
 * 並確保金額語意清晰（加、減、乘運算回傳新物件）。
 *
 * 不變量（Invariants）：
 * - 金額不得為負數（負數會在建構時拋出 DomainException）
 * - 所有金額統一使用小數點後 2 位，採用 HALF_UP 四捨五入
 *
 * 不可變（Immutable）：所有欄位為 final，所有運算回傳新的 Money 實例。
 * ============================================================
 */
public record Money(BigDecimal amount) {

    /** 金額零元的常數，避免重複建立物件 */
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    /**
     * 緊湊建構子：驗證金額不得為負數，並統一精度為 2 位小數。
     *
     * @throws DomainException 若 amount 為 null 或負數
     */
    public Money {
        if (amount == null) {
            throw new DomainException("金額不得為 null");
        }
        // 統一精度：小數點後 2 位，四捨五入
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("金額不得為負數，收到：" + amount);
        }
    }

    /**
     * 從 BigDecimal 建立 Money 的靜態工廠方法。
     *
     * @param amount 金額，不得為 null 或負數
     * @return 新的 Money 實例
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    /**
     * 從 long 整數建立 Money（例如 100 → 100.00 元）。
     *
     * @param amount 整數金額
     * @return 新的 Money 實例
     */
    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * 加法：this + other。
     *
     * @param other 加數，不得為 null
     * @return 相加後的新 Money 實例
     */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * 減法：this - other。
     * 若結果為負數，自動歸零（適用折扣超過原價的情境）。
     *
     * @param other 減數，不得為 null
     * @return 相減後的新 Money 實例（最小為 ZERO）
     */
    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        // 折扣金額超過原價時，最終金額歸零（不得為負）
        return new Money(result.max(BigDecimal.ZERO));
    }

    /**
     * 乘法：this × multiplier（用於計算小計 or 折扣比例）。
     *
     * @param multiplier 乘數
     * @return 相乘後的新 Money 實例
     */
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier));
    }

    /**
     * 比較兩個 Money 是否金額相等。
     * 使用 compareTo 而非 equals，避免 BigDecimal scale 不同導致誤判（如 1.0 ≠ 1.00）。
     *
     * @param other 比較對象
     * @return true 若金額數值相同
     */
    public boolean isEqualTo(Money other) {
        return this.amount.compareTo(other.amount) == 0;
    }

    /**
     * 判斷此金額是否大於 other。
     *
     * @param other 比較對象
     * @return true 若 this > other
     */
    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
