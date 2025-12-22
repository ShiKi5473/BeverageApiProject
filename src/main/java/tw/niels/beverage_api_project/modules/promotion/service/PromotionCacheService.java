package tw.niels.beverage_api_project.modules.promotion.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促銷活動快取服務
 * 將快取邏輯分離，避免在主 Service 中發生 Self-Invocation 問題。
 */
@Service
public class PromotionCacheService {

    private final PromotionRepository promotionRepository;

    public PromotionCacheService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    /**
     * 查詢有效活動並快取
     */
    @Cacheable(value = "promotions", key = "#brandId")
    @Transactional(readOnly = true)
    public List<Promotion> getActivePromotions(Long brandId) {
        return promotionRepository.findActivePromotionsByBrand(brandId, LocalDateTime.now());
    }
}