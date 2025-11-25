package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;

@Component
public class FixedAmountCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.FIXED_AMOUNT;
    }

    @Override
    public BigDecimal calculateDiscount(Order order, Promotion promotion) {
        BigDecimal orderTotal = order.getTotalAmount();
        BigDecimal minSpend = promotion.getMinSpend();

        // 檢查門檻
        if (minSpend != null && orderTotal.compareTo(minSpend) < 0) {
            return BigDecimal.ZERO;
        }

        // 回傳設定的固定金額 (例如 20 元)
        return promotion.getValue();
    }
}