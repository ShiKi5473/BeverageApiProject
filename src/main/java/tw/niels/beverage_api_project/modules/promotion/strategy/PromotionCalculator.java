package tw.niels.beverage_api_project.modules.promotion.strategy;

import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;

/**
 * 促銷折扣計算策略。
 * <p>
 * 修改（B-3）：輸入由 {@code OrderEntity + PromotionEntity} 改為 {@link PromotionContext} + {@link PromotionRule}，
 * 讓促銷計算與 JPA Entity／Order Aggregate 解耦，可同時用於結帳與金額試算（不需先建立訂單實體）。
 * </p>
 */
public interface PromotionCalculator {
    /**
     * 判斷此策略是否支援該促銷類型
     */
    boolean supports(PromotionType type);

    /**
     * 計算折扣金額（只回傳計算結果，不修改任何狀態）。
     * <p>
     * 實作應回傳 &gt;= 0 的金額；最終「不超過訂單總額、不為負數」的保證由 PromotionService 統一處理。
     * </p>
     *
     * @param context 訂單品項與總額
     * @param rule    促銷規則快照
     * @return 折扣金額（必須 &gt;= 0）
     */
    BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule);
}
