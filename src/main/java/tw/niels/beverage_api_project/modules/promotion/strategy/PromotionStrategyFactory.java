package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                .orElseThrow(() -> new IllegalArgumentException("不支援的促銷類型: " + type));
    }
}