package tw.niels.beverage_api_project.modules.promotion.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionJpaRepository;

/**
 * 促銷活動快取服務
 * 將快取邏輯分離，避免在主 Service 中發生 Self-Invocation 問題。
 */
@Service
public class PromotionCacheService {

    private final PromotionJpaRepository PromotionJpaRepository;

    public PromotionCacheService(PromotionJpaRepository PromotionJpaRepository) {
        this.PromotionJpaRepository = PromotionJpaRepository;
    }

    /**
     * 查詢有效活動並快取
     */
    @Cacheable(value = "promotions", key = "#brandId")
    @Transactional(readOnly = true)
    public List<PromotionEntity> getActivePromotions(Long brandId) {
        return PromotionJpaRepository.findActivePromotionEntitysByBrand(brandId, LocalDateTime.now());
    }
}