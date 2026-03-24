package tw.niels.beverage_api_project.modules.inventory.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 庫存數量值物件
 *
 * 封裝數值與單位（如：10.5 公斤、50 個），提供基本的數學運算並確保單位一致性。
 *
 * 不可變（Immutable）：使用 Java record。
 * ============================================================
 */
public record Quantity(BigDecimal value, String unit) {

    public Quantity {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        if (unit == null || unit.isBlank()) {
            unit = "UNIT";
        }
        // 數值標準化：保留 4 位小數（比 Money 精確，因為食材通常需要更高精度）
        value = value.setScale(4, RoundingMode.HALF_UP);
    }

    public static Quantity of(BigDecimal value, String unit) {
        return new Quantity(value, unit);
    }

    public static Quantity zero(String unit) {
        return new Quantity(BigDecimal.ZERO, unit);
    }

    // ─── 運算方法 ──────────────────────────────────────────────────────────

    public Quantity add(Quantity other) {
        checkUnit(other);
        return new Quantity(this.value.add(other.value), this.unit);
    }

    public Quantity subtract(Quantity other) {
        checkUnit(other);
        return new Quantity(this.value.subtract(other.value), this.unit);
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        checkUnit(other);
        return this.value.compareTo(other.value) >= 0;
    }

    public boolean isLessThan(Quantity other) {
        checkUnit(other);
        return this.value.compareTo(other.value) < 0;
    }

    private void checkUnit(Quantity other) {
        if (!Objects.equals(this.unit, other.unit)) {
            throw new IllegalArgumentException(
                    String.format("單位不一致：無法對 %s 與 %s 進行運算", this.unit, other.unit));
        }
    }

    @Override
    public String toString() {
        return value.stripTrailingZeros().toPlainString() + " " + unit;
    }
}
