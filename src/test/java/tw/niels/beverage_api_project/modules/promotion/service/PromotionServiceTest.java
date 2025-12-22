package tw.niels.beverage_api_project.modules.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;
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

    // 我們在這個測試中不 mock Repository，而是 mock "self" 代理物件的方法回傳值
    // 或者直接測試 calculateBestDiscount 邏輯。
    // 由於 calculateBestDiscount 呼叫了 self.getActivePromotions，我們需要 Mock self
    @Mock private PromotionService selfProxy;

    @InjectMocks
    private PromotionService promotionService;

    @BeforeEach
    void setup() {
        // 手動注入 self 代理，模擬 Spring 的行為
        ReflectionTestUtils.setField(promotionService, "self", selfProxy);
    }

    @Test
    @DisplayName("計算最佳折扣 - 比較多個活動取最優")
    void calculateBestDiscount_ShouldPickHighest() {
        // Arrange
        Long brandId = 1L;
        Brand brand = new Brand();
        brand.setId(brandId);

        Order order = new Order();
        order.setBrand(brand);
        order.setTotalAmount(new BigDecimal("1000.00"));

        // 活動 A: 滿 500 折 100 (Fixed)
        Promotion promoA = new Promotion();
        promoA.setType(PromotionType.FIXED_AMOUNT);
        promoA.setMinSpend(new BigDecimal("500"));
        promoA.setValue(new BigDecimal("100"));

        // 活動 B: 全館 8 折 (Percentage) => 1000 * 0.2 = 200
        Promotion promoB = new Promotion();
        promoB.setType(PromotionType.PERCENTAGE);
        promoB.setValue(new BigDecimal("0.8")); // 8折

        // Mock self.getActivePromotions
        when(selfProxy.getActivePromotions(brandId)).thenReturn(Arrays.asList(promoA, promoB));

        // Mock Strategy Factory
        PromotionCalculator fixedCalc = new FixedAmountCalculator();
        PromotionCalculator pctCalc = new PercentageCalculator();

        when(strategyFactory.getCalculator(PromotionType.FIXED_AMOUNT)).thenReturn(fixedCalc);
        when(strategyFactory.getCalculator(PromotionType.PERCENTAGE)).thenReturn(pctCalc);

        // Act
        BigDecimal result = promotionService.calculateBestDiscount(order);

        // Assert
        // 應該選 200 (8折) 而不是 100 (固定折扣)
        assertThat(result).isEqualByComparingTo("200.00");
    }
}