package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 組合優惠：適用商品清單中的每項商品各一件組成一套，整套以 value 元計價。
 * <p>
 * 修改（B-3）：
 * <ul>
 *   <li>改吃 {@link PromotionContext}，不再讀取 OrderEntity／ProductEntity。</li>
 *   <li>同商品有多種單價（不同規格或加料）時，原本取「第一筆」的單價（結果取決於集合順序，不穩定），
 *       改為取最低單價，計算結果固定且不會高估折扣。</li>
 * </ul>
 * </p>
 */
@Component
public class BundleDiscountCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.BUNDLE_DISCOUNT;
    }

    @Override
    public BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule) {
        Set<Long> requiredProductIds = rule.applicableProductIds();
        BigDecimal bundlePrice = rule.value();

        // 組合活動必須指定商品，且組合價不可為負
        if (requiredProductIds.isEmpty() || bundlePrice == null || bundlePrice.signum() < 0) {
            return BigDecimal.ZERO;
        }

        // 統計訂單中各適用商品的數量與最低單價
        Map<Long, Long> cartCounts = new HashMap<>();
        Map<Long, BigDecimal> cartMinPrices = new HashMap<>();
        for (PromotionContext.Line line : PromotionLines.eligibleLines(context, rule)) {
            cartCounts.merge(line.productId(), (long) line.quantity(), Long::sum);
            cartMinPrices.merge(line.productId(), PromotionLines.unitPriceOf(line), BigDecimal::min);
        }

        // 計算可以組成幾套組合，並累加單套原價
        long possibleSets = Long.MAX_VALUE;
        BigDecimal originalBundlePrice = BigDecimal.ZERO;
        for (Long pid : requiredProductIds) {
            long count = cartCounts.getOrDefault(pid, 0L);
            if (count == 0) {
                return BigDecimal.ZERO; // 缺件，無法組成
            }
            possibleSets = Math.min(possibleSets, count);
            originalBundlePrice = originalBundlePrice.add(cartMinPrices.get(pid));
        }

        // 單套折扣 = 原價總和 - 組合價；組合價比原價還貴時不折抵
        BigDecimal bundleDiscount = originalBundlePrice.subtract(bundlePrice);
        if (bundleDiscount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return bundleDiscount.multiply(BigDecimal.valueOf(possibleSets));
    }
}
