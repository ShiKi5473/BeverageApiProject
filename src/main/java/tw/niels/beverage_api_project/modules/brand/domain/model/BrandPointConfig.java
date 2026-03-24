package tw.niels.beverage_api_project.modules.brand.domain.model;

import java.math.BigDecimal;

/**
 * ============================================================
 * Bounded Context: Brand & Store
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 品牌積點配置值物件
 *
 * 封裝品牌層級的累積與折抵規則。
 *
 * 不可變（Immutable）：使用 Java record。
 * ============================================================
 */
public record BrandPointConfig(
        /** 累積匯率：每消費多少元獲得 1 點 */
        BigDecimal earnRate,

        /** 折抵匯率：每 1 點可折抵多少元 */
        BigDecimal redeemRate,

        /** 點數過期天數（null 代表永不過期） */
        Integer expiryDays
) {
    public BrandPointConfig {
        if (earnRate == null || earnRate.compareTo(BigDecimal.ZERO) <= 0) {
            earnRate = BigDecimal.ONE;
        }
        if (redeemRate == null || redeemRate.compareTo(BigDecimal.ZERO) < 0) {
            redeemRate = new BigDecimal("0.1");
        }
    }

    public static BrandPointConfig defaultSettings() {
        return new BrandPointConfig(BigDecimal.ONE, new BigDecimal("0.1"), null);
    }
}
