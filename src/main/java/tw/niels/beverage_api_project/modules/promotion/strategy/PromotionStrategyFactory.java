package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.util.List;

@Component
public class PromotionStrategyFactory {

    private final List<PromotionCalculator> calculators;

    public PromotionStrategyFactory(List<PromotionCalculator> calculators) {
        this.calculators = calculators;
    }

    public PromotionCalculator getCalculator(PromotionType type) {
        return calculators.stream()
                .filter(c -> c.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("尚未實作此促銷類型的計算邏輯: " + type));
    }
}