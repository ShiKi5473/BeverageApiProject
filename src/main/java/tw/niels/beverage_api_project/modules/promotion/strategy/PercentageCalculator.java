package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PercentageCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.PERCENTAGE;
    }

    @Override
    public BigDecimal calculateDiscount(Order order, Promotion promotion) {
        BigDecimal orderTotal = order.getTotalAmount();
        BigDecimal minSpend = promotion.getMinSpend();

        if (minSpend != null && orderTotal.compareTo(minSpend) < 0) {
            return BigDecimal.ZERO;
        }

        // promotion.getValue() 是折數 (例如 0.9 代表 9 折)
        // 折扣金額 = 總金額 * (1 - 折數)
        // 例如：100 * (1 - 0.9) = 10 元折扣
        BigDecimal discountRate = BigDecimal.ONE.subtract(promotion.getValue());

        // 防呆：折數不能負數
        if (discountRate.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;

        return orderTotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
    }
}