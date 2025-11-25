package tw.niels.beverage_api_project.modules.promotion.service;

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
    /**
     * 建立新促銷活動
     */
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
        promotion.setActive(true);

        // 處理適用商品 (若有指定)
        if (request.getApplicableProductIds() != null && !request.getApplicableProductIds().isEmpty()) {
            Set<Product> products = new HashSet<>();
            for (Long pid : request.getApplicableProductIds()) {
                // 使用帶 brandId 的安全查詢
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
     * 關閉/刪除活動 (軟刪除：設為 inactive)
     */
    @Transactional
    public void deactivatePromotion(Long brandId, Long promotionId) {
        Promotion promotion = promotionRepository.findByBrand_IdAndId(brandId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到促銷活動 ID: " + promotionId));

        promotion.setActive(false);
        promotionRepository.save(promotion);
    }
}