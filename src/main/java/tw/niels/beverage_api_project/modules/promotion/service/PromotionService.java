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
     * 目前策略：擇優使用 (挑選折扣金額最高的一個活動)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBestDiscount(Order order) {
        Long brandId = order.getBrand().getBrandId();
        List<Promotion> activePromotions = promotionRepository.findActivePromotionsByBrand(brandId, LocalDateTime.now());

        BigDecimal maxDiscount = BigDecimal.ZERO;

        for (Promotion promotion : activePromotions) {
            try {
                PromotionCalculator calculator = strategyFactory.getCalculator(promotion.getType());
                BigDecimal discount = calculator.calculateDiscount(order, promotion);

                if (discount.compareTo(maxDiscount) > 0) {
                    maxDiscount = discount;
                    // TODO: 未來可以在這裡記錄 order 使用了哪個 promotion (order 需要新增 promotion_id 欄位)
                }
            } catch (Exception e) {
                // 忽略計算錯誤的促銷，繼續下一個
                System.err.println("促銷計算失敗 ID: " + promotion.getPromotionId() + " Error: " + e.getMessage());
            }
        }

        return maxDiscount;
    }
}