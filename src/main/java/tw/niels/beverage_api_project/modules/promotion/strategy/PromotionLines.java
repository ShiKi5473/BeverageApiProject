package tw.niels.beverage_api_project.modules.promotion.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;

/**
 * 促銷策略共用的品項計算工具（新增，B-3）。
 * <p>
 * 各策略改吃 {@link PromotionContext} 後，「哪些品項適用活動」「品項單價／小計」「折扣計算基準」
 * 等邏輯集中在這裡，避免四個策略各自實作而不一致。
 * 所有方法都對 null 值做防護（null 金額視為 0、數量 &lt;= 0 的品項忽略）。
 * </p>
 */
final class PromotionLines {

    private PromotionLines() {
    }

    /**
     * 取出適用此活動的品項：全館活動取全部品項，否則只取 productId 在適用清單中的品項。
     * 數量 &lt;= 0 或沒有 productId 的品項一律忽略。
     */
    static List<PromotionContext.Line> eligibleLines(PromotionContext context, PromotionRule rule) {
        List<PromotionContext.Line> result = new ArrayList<>();
        if (context == null || context.lines() == null) {
            return result;
        }
        for (PromotionContext.Line line : context.lines()) {
            if (line != null && line.quantity() > 0 && rule.coversProduct(line.productId())) {
                result.add(line);
            }
        }
        return result;
    }

    /** 品項小計：優先使用 subtotal，缺值時以 unitPrice × quantity 計算 */
    static BigDecimal subtotalOf(PromotionContext.Line line) {
        if (line.subtotal() != null) {
            return line.subtotal();
        }
        if (line.unitPrice() != null) {
            return line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
        }
        return BigDecimal.ZERO;
    }

    /** 品項單價：優先使用 unitPrice，缺值時以 subtotal ÷ quantity 推算 */
    static BigDecimal unitPriceOf(PromotionContext.Line line) {
        if (line.unitPrice() != null) {
            return line.unitPrice();
        }
        if (line.subtotal() != null && line.quantity() > 0) {
            return line.subtotal().divide(BigDecimal.valueOf(line.quantity()), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 折扣計算基準金額。
     * <ul>
     *   <li>全館活動：訂單總額（與舊版以 OrderEntity.totalAmount 計算的行為相同）。</li>
     *   <li>指定商品活動：只加總適用品項的小計。舊版 FIXED_AMOUNT／PERCENTAGE 完全忽略適用商品清單，
     *       會把「指定商品 9 折」套用到整張訂單，現修正為只計算適用品項。</li>
     * </ul>
     */
    static BigDecimal discountBase(PromotionContext context, PromotionRule rule) {
        if (rule.appliesToAllProducts()) {
            return context.totalAmount() != null ? context.totalAmount() : BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PromotionContext.Line line : eligibleLines(context, rule)) {
            sum = sum.add(subtotalOf(line));
        }
        return sum;
    }

    /** 是否達到最低消費門檻（未設定門檻視為達到） */
    static boolean meetsMinSpend(BigDecimal base, PromotionRule rule) {
        return rule.minSpend() == null || base.compareTo(rule.minSpend()) >= 0;
    }
}
