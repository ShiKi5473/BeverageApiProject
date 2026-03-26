package tw.niels.beverage_api_project.modules.promotion.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.dto.CreatePromotionRequestDto;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

@Service
public class PromotionService {

    private static final Logger logger = LoggerFactory.getLogger(PromotionService.class);

    private final PromotionJpaRepository PromotionJpaRepository;
    private final PromotionStrategyFactory strategyFactory;
    private final BrandJpaRepository brandRepository;
    private final ProductJpaRepository productRepository;
    private final PromotionCacheService promotionCacheService;

    public PromotionService(PromotionJpaRepository PromotionJpaRepository,
                            PromotionStrategyFactory strategyFactory,
                            BrandJpaRepository brandRepository,
                            ProductJpaRepository productRepository,
                            PromotionCacheService promotionCacheService) {
        this.PromotionJpaRepository = PromotionJpaRepository;
        this.strategyFactory = strategyFactory;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.promotionCacheService = promotionCacheService;
    }

    /**
     * 查詢有效活動並快取
     * Key 為 brandId，當該品牌的活動有變更時清除。
     * 注意：必須是 public 方法，且由外部或其他 Bean (如 self) 呼叫才能觸發快取。
     */
    @Cacheable(value = "promotions", key = "#brandId")
    @Transactional(readOnly = true)
    public List<PromotionEntity> getActivePromotions(Long brandId) {
        return PromotionJpaRepository.findActivePromotionEntitysByBrand(brandId, LocalDateTime.now());
    }

    /**
     * 計算訂單的最佳促銷折扣
     */
    public BigDecimal calculateBestDiscount(OrderEntity OrderEntity) {
        if (OrderEntity.getBrand() == null) return BigDecimal.ZERO;

        Long brandId = OrderEntity.getBrand().getId();

        // 改為呼叫外部 Cache Service，無需 Self-Injection
        List<PromotionEntity> activePromotions = promotionCacheService.getActivePromotions(brandId);

        BigDecimal maxDiscount = BigDecimal.ZERO;

        for (PromotionEntity promotion : activePromotions) {
            try {
                PromotionCalculator calculator = strategyFactory.getCalculator(promotion.getType());
                BigDecimal discount = calculator.calculateDiscount(OrderEntity, promotion);

                if (discount.compareTo(maxDiscount) > 0) {
                    maxDiscount = discount;
                }
            } catch (Exception e) {
                // 個別促銷計算失敗不應中斷整體折扣計算，記錄警告後跳過該活動
                logger.warn("促銷活動 '{}' (ID: {}) 折扣計算失敗，已跳過: {}",
                        promotion.getName(), promotion.getId(), e.getMessage());
            }
        }

        return maxDiscount;
    }

    /**
     * 建立新促銷活動
     */
    @CacheEvict(value = "promotions", key = "#brandId") // 【新增】清除快取
    @Transactional
    public PromotionEntity createPromotion(Long brandId, CreatePromotionRequestDto request) {
        BrandEntity BrandEntity = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌 ID: " + brandId));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("結束時間必須晚於開始時間");
        }

        PromotionEntity promotion = new PromotionEntity();
        promotion.setBrand(BrandEntity);
        promotion.setName(request.getName());
        promotion.setDescription(request.getDescription());
        promotion.setType(request.getType());
        promotion.setValue(request.getValue());
        promotion.setMinSpend(request.getMinSpend());
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setIsActive(true);

        if (request.getApplicableProductIds() != null && !request.getApplicableProductIds().isEmpty()) {
            Set<ProductEntity> products = new HashSet<>();
            for (Long pid : request.getApplicableProductIds()) {
                ProductEntity p = productRepository.findByBrand_IdAndId(brandId, pid)
                        .orElseThrow(() -> new BadRequestException("商品 ID " + pid + " 無效或不屬於此品牌"));
                products.add(p);
            }
            promotion.setApplicableProducts(products);
        }

        return PromotionJpaRepository.save(promotion);
    }

    /**
     * 查詢品牌的所有活動
     */
    @Transactional(readOnly = true)
    public List<PromotionEntity> getPromotionsByBrand(Long brandId) {
        return PromotionJpaRepository.findByBrand_Id(brandId);
    }


    /**
     * 關閉/刪除活動
     */
    @CacheEvict(value = "promotions", key = "#brandId") // 【新增】清除快取
    @Transactional
    public void deactivatePromotion(Long brandId, Long promotionId) {
        PromotionEntity promotion = PromotionJpaRepository.findByBrand_IdAndId(brandId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到促銷活動 ID: " + promotionId));

        promotion.setIsActive(false);
        PromotionJpaRepository.save(promotion);
    }
}