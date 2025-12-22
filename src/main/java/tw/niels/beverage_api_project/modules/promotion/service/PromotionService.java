package tw.niels.beverage_api_project.modules.promotion.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.promotion.dto.CreatePromotionRequestDto;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PromotionService {

    // 自我注入 (Self-Injection) 以解決 AOP 失效問題
    @Autowired
    @Lazy
    private PromotionService self;

    private final PromotionRepository promotionRepository;
    private final PromotionStrategyFactory strategyFactory;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public PromotionService(PromotionRepository promotionRepository,
                            PromotionStrategyFactory strategyFactory,
                            BrandRepository brandRepository,
                            ProductRepository productRepository) {
        this.promotionRepository = promotionRepository;
        this.strategyFactory = strategyFactory;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    /**
     * 查詢有效活動並快取
     * Key 為 brandId，當該品牌的活動有變更時清除。
     * 注意：必須是 public 方法，且由外部或其他 Bean (如 self) 呼叫才能觸發快取。
     */
    @Cacheable(value = "promotions", key = "#brandId")
    @Transactional(readOnly = true)
    public List<Promotion> getActivePromotions(Long brandId) {
        return promotionRepository.findActivePromotionsByBrand(brandId, LocalDateTime.now());
    }

    /**
     * 計算訂單的最佳促銷折扣
     */
    public BigDecimal calculateBestDiscount(Order order) {
        if (order.getBrand() == null) return BigDecimal.ZERO;

        Long brandId = order.getBrand().getId();

        // 透過 self 代理物件呼叫，觸發 @Cacheable 機制
        List<Promotion> activePromotions = self.getActivePromotions(brandId);

        BigDecimal maxDiscount = BigDecimal.ZERO;

        for (Promotion promotion : activePromotions) {
            try {
                PromotionCalculator calculator = strategyFactory.getCalculator(promotion.getType());
                BigDecimal discount = calculator.calculateDiscount(order, promotion);

                if (discount.compareTo(maxDiscount) > 0) {
                    maxDiscount = discount;
                }
            } catch (Exception e) {
                // 忽略不支援或計算錯誤的活動
            }
        }

        return maxDiscount;
    }

    /**
     * 建立新促銷活動
     */
    @CacheEvict(value = "promotions", key = "#brandId") // 【新增】清除快取
    @Transactional
    public Promotion createPromotion(Long brandId, CreatePromotionRequestDto request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌 ID: " + brandId));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("結束時間必須晚於開始時間");
        }

        Promotion promotion = new Promotion();
        promotion.setBrand(brand);
        promotion.setName(request.getName());
        promotion.setDescription(request.getDescription());
        promotion.setType(request.getType());
        promotion.setValue(request.getValue());
        promotion.setMinSpend(request.getMinSpend());
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setIsActive(true);

        if (request.getApplicableProductIds() != null && !request.getApplicableProductIds().isEmpty()) {
            Set<Product> products = new HashSet<>();
            for (Long pid : request.getApplicableProductIds()) {
                Product p = productRepository.findByBrand_IdAndId(brandId, pid)
                        .orElseThrow(() -> new BadRequestException("商品 ID " + pid + " 無效或不屬於此品牌"));
                products.add(p);
            }
            promotion.setApplicableProducts(products);
        }

        return promotionRepository.save(promotion);
    }

    /**
     * 查詢品牌的所有活動
     */
    @Transactional(readOnly = true)
    public List<Promotion> getPromotionsByBrand(Long brandId) {
        return promotionRepository.findByBrand_Id(brandId);
    }


    /**
     * 關閉/刪除活動
     */
    @CacheEvict(value = "promotions", key = "#brandId") // 【新增】清除快取
    @Transactional
    public void deactivatePromotion(Long brandId, Long promotionId) {
        Promotion promotion = promotionRepository.findByBrand_IdAndId(brandId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到促銷活動 ID: " + promotionId));

        promotion.setIsActive(false);
        promotionRepository.save(promotion);
    }
}