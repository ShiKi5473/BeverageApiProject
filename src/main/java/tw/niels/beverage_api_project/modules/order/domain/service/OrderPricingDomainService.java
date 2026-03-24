package tw.niels.beverage_api_project.modules.order.domain.service;

import tw.niels.beverage_api_project.common.domain.vo.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Domain Service
 * ============================================================
 *
 * 訂單定價領域服務
 *
 * 處理跨 Aggregate 的複雜計算邏輯，不持有狀態。
 * 此類別不涉及資料庫存取，純粹進行業務規則運算。
 *
 * 職責：
 * 1. 根據訂單金額與積點規則計算可獲得點數。
 * 2. 協調複雜的金額試算邏輯。
 * ============================================================
 */
public class OrderPricingDomainService {

    /**
     * 計算訂單應獲得的積點數量。
     *
     * 業務規則：
     * - 積點計算以「最終實付金額」為準（已扣除折扣）。
     * - 採用無條件捨去（Floor）確保點數為整數。
     * - earnRate 代表「每消費多少元回饋 1 點」。
     *
     * @param finalAmount 最終實付金額
     * @param earnRate    累積匯率（每消費多少元獲得 1 點）
     * @return 本次訂單應累積的積點
     */
    public Long calculateEarnedPoints(Money finalAmount, BigDecimal earnRate) {
        if (finalAmount == null || earnRate == null || earnRate.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }

        // points = floor(finalAmount / earnRate)
        return finalAmount.amount()
                .divide(earnRate, 0, RoundingMode.FLOOR)
                .longValue();
    }

    /**
     * 計算點數可以折抵的金額。
     *
     * @param pointsToUse 要使用的點數
     * @param redeemRate  折抵匯率（每 1 點可折抵多少元）
     * @return 折抵金額
     */
    public Money calculateRedeemAmount(Long pointsToUse, BigDecimal redeemRate) {
        if (pointsToUse == null || pointsToUse <= 0 || redeemRate == null) {
            return Money.ZERO;
        }

        BigDecimal discount = BigDecimal.valueOf(pointsToUse).multiply(redeemRate);
        return Money.of(discount);
    }
}
