package tw.niels.beverage_api_project.modules.promotion.strategy;

import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;

public interface PromotionCalculator {
    /**
     * 判斷此策略是否支援該促銷類型
     */
    boolean supports(PromotionType type);

    /**
     * 計算折扣金額 (不修改訂單，只回傳計算結果)
     * @return 折扣金額 (必須 >= 0)
     */
    BigDecimal calculateDiscount(Order order, Promotion promotion);
}