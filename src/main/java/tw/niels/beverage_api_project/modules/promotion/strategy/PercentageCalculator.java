package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 百分比折扣：滿 minSpend 打 value 折（0.85 = 85 折）。
 * <p>
 * 修改（B-3）：改吃 {@link PromotionContext}；指定商品的活動只對適用品項小計打折
 * （舊版忽略適用商品清單，會把整張訂單都打折）。
 * </p>
 */
@Component
public class PercentageCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.PERCENTAGE;
    }

    @Override
    public BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule) {
        BigDecimal base = PromotionLines.discountBase(context, rule);
        BigDecimal rate = rule.value();

        // 折數必須介於 0 與 1 之間（不含）；1 以上代表不打折或設定錯誤，一律不折扣
        if (base.signum() <= 0 || rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0
                || !PromotionLines.meetsMinSpend(base, rule)) {
            return BigDecimal.ZERO;
        }

        // 折扣金額 = 計算基準 × (1 - 折數)，例如：100 × (1 - 0.85) = 15 元
        return base.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
    }
}
