package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.util.List;
import java.util.Optional;

@Component
public class PromotionStrategyFactory {

    private final List<PromotionCalculator> calculators;

    public PromotionStrategyFactory(List<PromotionCalculator> calculators) {
        this.calculators = calculators;
    }

    public PromotionCalculator getCalculator(PromotionType type) {
        return findCalculator(type)
                .orElseThrow(() -> new IllegalArgumentException("尚未實作此促銷類型的計算邏輯: " + type));
    }

    /**
     * 新增：查詢支援該類型的計算策略，找不到時回傳 empty（不拋例外）。
     * 例如 GIFT_WITH_PURCHASE（滿額贈）不影響訂單金額、沒有計算策略，
     * PromotionService 直接略過，不必每次結帳都拋例外再寫一行警告日誌。
     */
    public Optional<PromotionCalculator> findCalculator(PromotionType type) {
        return calculators.stream()
                .filter(c -> c.supports(type))
                .findFirst();
    }
}
