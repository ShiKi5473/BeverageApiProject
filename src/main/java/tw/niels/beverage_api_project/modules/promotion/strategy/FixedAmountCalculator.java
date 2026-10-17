package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;

/**
 * 滿額折抵：滿 minSpend 折 value 元。
 * <p>
 * 修改（B-3）：改吃 {@link PromotionContext}；指定商品的活動只以適用品項小計判斷門檻；
 * 折抵金額不超過計算基準（例如「折 100 元」遇到 60 元的適用品項，只折 60 元）。
 * </p>
 */
@Component
public class FixedAmountCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.FIXED_AMOUNT;
    }

    @Override
    public BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule) {
        BigDecimal base = PromotionLines.discountBase(context, rule);
        BigDecimal value = rule.value();

        // 沒有可折抵的金額、折抵值無效，或未達門檻 → 不折扣
        if (base.signum() <= 0 || value == null || value.signum() <= 0
                || !PromotionLines.meetsMinSpend(base, rule)) {
            return BigDecimal.ZERO;
        }

        // 回傳設定的固定金額（例如 20 元），但不超過計算基準
        return value.min(base);
    }
}
