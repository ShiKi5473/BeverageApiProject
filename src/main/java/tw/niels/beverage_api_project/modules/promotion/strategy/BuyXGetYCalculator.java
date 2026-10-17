package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 買 X 送 Y：適用商品每湊滿 (X + Y) 件，最便宜的 Y 件免費。
 * <p>
 * 參數沿用既有設定方式：X = minSpend（需購買數，未設定為 1），Y = value（贈送數）。
 * </p>
 * <p>
 * 修改（B-3）：
 * <ul>
 *   <li>改吃 {@link PromotionContext}，不再讀取 OrderEntity。</li>
 *   <li>不再把每一件展開成單價清單（數量很大時會產生大量物件），
 *       改為依單價由低到高排序品項後，直接累加最便宜的 Y × 組數 件。</li>
 *   <li>X &lt; 1 時視為 1（避免 X = 0 造成「每件都免費」），Y &lt; 1 時不折扣。</li>
 * </ul>
 * </p>
 */
@Component
public class BuyXGetYCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.BUY_X_GET_Y;
    }

    @Override
    public BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule) {
        // 1. 解析 X（需購買數）與 Y（贈送數）
        int buyCount = rule.minSpend() != null ? Math.max(1, rule.minSpend().intValue()) : 1;
        int freeCount = rule.value() != null ? rule.value().intValue() : 1;
        if (freeCount < 1) {
            return BigDecimal.ZERO;
        }
        long groupSize = (long) buyCount + freeCount;

        // 2. 取得適用品項，依單價由低到高排序（贈送最便宜的）
        List<PromotionContext.Line> lines = PromotionLines.eligibleLines(context, rule);
        if (lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        lines.sort(Comparator.comparing(PromotionLines::unitPriceOf));

        // 3. 計算可湊成的組數與免費件數
        long totalUnits = 0;
        for (PromotionContext.Line line : lines) {
            totalUnits += line.quantity();
        }
        long freeUnits = (totalUnits / groupSize) * freeCount;

        // 4. 從最便宜的品項開始累加免費件數的金額
        return sumCheapestUnits(lines, freeUnits);
    }

    /**
     * 從已排序（單價由低到高）的品項中，累加最便宜的 freeUnits 件金額。
     */
    private BigDecimal sumCheapestUnits(List<PromotionContext.Line> sortedLines, long freeUnits) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        long remaining = freeUnits;
        for (PromotionContext.Line line : sortedLines) {
            if (remaining <= 0) {
                break;
            }
            long take = Math.min(remaining, line.quantity());
            totalDiscount = totalDiscount.add(PromotionLines.unitPriceOf(line).multiply(BigDecimal.valueOf(take)));
            remaining -= take;
        }
        return totalDiscount;
    }
}
