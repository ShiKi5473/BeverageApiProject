package tw.niels.beverage_api_project.modules.promotion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionStrategyFactory strategyFactory;

    public PromotionService(PromotionRepository promotionRepository, PromotionStrategyFactory strategyFactory) {
        this.promotionRepository = promotionRepository;
        this.strategyFactory = strategyFactory;
    }

    /**
     * 計算訂單的最佳促銷折扣
     * 策略：擇優使用 (挑選折扣金額最高的一個活動)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBestDiscount(Order order) {
        // 確保有 Brand Id
        if (order.getBrand() == null) return BigDecimal.ZERO;

        Long brandId = order.getBrand().getBrandId();
        List<Promotion> activePromotions = promotionRepository.findActivePromotionsByBrand(brandId, LocalDateTime.now());

        BigDecimal maxDiscount = BigDecimal.ZERO;

        for (Promotion promotion : activePromotions) {
            try {
                PromotionCalculator calculator = strategyFactory.getCalculator(promotion.getType());
                BigDecimal discount = calculator.calculateDiscount(order, promotion);

                if (discount.compareTo(maxDiscount) > 0) {
                    maxDiscount = discount;
                    // TODO: 未來可在此記錄使用哪個 Promotion ID
                }
            } catch (Exception e) {
                // 遇到不支援的類型或計算錯誤，跳過該活動，不影響結帳
                System.err.println("促銷計算跳過 (ID: " + promotion.getPromotionId() + "): " + e.getMessage());
            }
        }

        return maxDiscount;
    }
}