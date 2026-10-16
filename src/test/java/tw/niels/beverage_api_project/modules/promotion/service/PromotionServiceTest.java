package tw.niels.beverage_api_project.modules.promotion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// 修正：Entity 類別必須加上 Entity 後綴，Repository 必須加上 Jpa 前綴
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.FixedAmountCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PercentageCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionStrategyFactory strategyFactory;

    // 新增 PromotionCacheService 的 Mock
    @Mock private PromotionCacheService promotionCacheService;

    // 為了讓 @InjectMocks 順利透過建構子注入，建議也 Mock 其他 Repository
    // 即使在這個測試案例中沒有直接使用到它們
    @Mock private PromotionJpaRepository promotionRepository;
    @Mock private BrandJpaRepository brandRepository;
    @Mock private ProductJpaRepository productRepository;

    @InjectMocks
    private PromotionService promotionService;


    @Test
    @DisplayName("計算最佳折扣 - 比較多個活動取最優")
    void calculateBestDiscount_ShouldPickHighest() {
        // Arrange
        Long brandId = 1L;
        BrandEntity brand = new BrandEntity();
        brand.setId(brandId);

        OrderEntity order = new OrderEntity();
        order.setBrand(brand);
        order.setTotalAmount(new BigDecimal("1000.00"));

        // 活動 A: 滿 500 折 100 (Fixed)
        PromotionEntity promoA = new PromotionEntity();
        promoA.setType(PromotionType.FIXED_AMOUNT);
        promoA.setMinSpend(new BigDecimal("500"));
        promoA.setValue(new BigDecimal("100"));

        // 活動 B: 全館 8 折 (Percentage) => 1000 * 0.2 = 200
        PromotionEntity promoB = new PromotionEntity();
        promoB.setType(PromotionType.PERCENTAGE);
        promoB.setValue(new BigDecimal("0.8")); // 8折

        // 修改為 Mock promotionCacheService.getActivePromotions
        when(promotionCacheService.getActivePromotions(brandId)).thenReturn(Arrays.asList(promoA, promoB));

        // Mock Strategy Factory
        PromotionCalculator fixedCalc = new FixedAmountCalculator();
        PromotionCalculator pctCalc = new PercentageCalculator();

        when(strategyFactory.getCalculator(PromotionType.FIXED_AMOUNT)).thenReturn(fixedCalc);
        when(strategyFactory.getCalculator(PromotionType.PERCENTAGE)).thenReturn(pctCalc);

        // Act
        BigDecimal result = promotionService.calculateBestDiscount(order);

        // Assert
        // 應該選 200 (8折) 而���是 100 (固定折扣)
        assertThat(result).isEqualByComparingTo("200.00");
    }
}
