package tw.niels.beverage_api_project.modules.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.BundleDiscountCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.BuyXGetYCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.FixedAmountCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PercentageCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PromotionService 單元測試
 * <p>
 * 修改（B-3）：
 * - 改以 {@link PromotionContext} 計算折扣（舊的 OrderEntity 版本已移除）。
 * - PromotionCacheService 已刪除，快取改由 PromotionService 透過 CacheManager 存取 PromotionRule DTO；
 *   測試使用記憶體版 ConcurrentMapCacheManager 與固定時鐘，不需 Redis。
 * - 使用真實的計算策略（不再 mock StrategyFactory），同時驗證各策略與「折扣不超過總額、不為負」的保證。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    /** 固定「現在」時間：2026-10-17 12:00（台北） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Mock private PromotionJpaRepository promotionRepository;
    @Mock private BrandJpaRepository brandRepository;
    @Mock private ProductJpaRepository productRepository;

    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionService = newService(List.of(
                new FixedAmountCalculator(),
                new PercentageCalculator(),
                new BuyXGetYCalculator(),
                new BundleDiscountCalculator()));
    }

    /** 以指定的計算策略建立 Service（記憶體快取 + 固定時鐘） */
    private PromotionService newService(List<PromotionCalculator> calculators) {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        return new PromotionService(
                promotionRepository,
                new PromotionStrategyFactory(calculators),
                brandRepository,
                productRepository,
                new ConcurrentMapCacheManager(PromotionService.RULE_CACHE),
                fixedClock);
    }

    // ─── 測試資料工具 ──────────────────────────────────────────────────────

    private static PromotionEntity promotion(PromotionType type, String value, String minSpend, Long... productIds) {
        PromotionEntity p = new PromotionEntity();
        p.setName(type.name());
        p.setType(type);
        p.setValue(value != null ? new BigDecimal(value) : null);
        p.setMinSpend(minSpend != null ? new BigDecimal(minSpend) : null);
        p.setStartDate(NOW.minusDays(1));
        p.setEndDate(NOW.plusDays(1));
        p.setIsActive(true);
        Set<ProductEntity> products = new HashSet<>();
        for (Long pid : productIds) {
            ProductEntity product = new ProductEntity();
            product.setId(pid);
            products.add(product);
        }
        p.setApplicableProducts(products);
        return p;
    }

    private static PromotionContext.Line line(Long productId, int qty, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return new PromotionContext.Line(productId, qty, price, price.multiply(BigDecimal.valueOf(qty)));
    }

    private static PromotionContext context(PromotionContext.Line... lines) {
        BigDecimal total = Arrays.stream(lines)
                .map(PromotionContext.Line::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PromotionContext(BRAND_ID, new ArrayList<>(List.of(lines)), total);
    }

    private void givenPromotions(PromotionEntity... promotions) {
        when(promotionRepository.findNotEndedByBrandWithProducts(eq(BRAND_ID), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>(List.of(promotions)));
    }

    // ─── 測試案例 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("計算最佳折扣 - 比較多個活動取最優")
    void calculateBestDiscount_ShouldPickHighest() {
        // 活動 A：滿 500 折 100（固定金額）；活動 B：全館 8 折 => 1000 × 0.2 = 200
        givenPromotions(
                promotion(PromotionType.FIXED_AMOUNT, "100", "500"),
                promotion(PromotionType.PERCENTAGE, "0.8", null));

        BigDecimal result = promotionService.calculateBestDiscount(context(line(10L, 10, "100")));

        // 應該選 200（8 折）而不是 100（固定折扣）
        assertThat(result).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("折扣不得超過訂單總額")
    void calculateBestDiscount_ShouldNotExceedTotal() {
        // 無門檻折 100 元，但訂單只有 60 元
        givenPromotions(promotion(PromotionType.FIXED_AMOUNT, "100", null));

        BigDecimal result = promotionService.calculateBestDiscount(context(line(10L, 1, "60")));

        assertThat(result).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("未達門檻、沒有活動或無效輸入時折扣為 0（不為負數）")
    void calculateBestDiscount_ShouldReturnZeroWhenNotApplicable() {
        givenPromotions(promotion(PromotionType.FIXED_AMOUNT, "50", "500"));

        assertThat(promotionService.calculateBestDiscount(context(line(10L, 2, "100")))).isEqualByComparingTo("0");
        assertThat(promotionService.calculateBestDiscount(null)).isEqualByComparingTo("0");
        assertThat(promotionService.calculateBestDiscount(new PromotionContext(BRAND_ID, List.of(), BigDecimal.ZERO)))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("指定商品的百分比折扣只套用在適用品項")
    void percentage_ShouldOnlyApplyToApplicableProducts() {
        // 商品 10 打 9 折；訂單：商品 10 共 200 元、商品 20 共 300 元 → 折 20 元（不是 50 元）
        givenPromotions(promotion(PromotionType.PERCENTAGE, "0.9", null, 10L));

        BigDecimal result = promotionService.calculateBestDiscount(
                context(line(10L, 2, "100"), line(20L, 3, "100")));

        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("買一送一：送最便宜的品項")
    void buyXGetY_ShouldGiveCheapestItemsFree() {
        // X = 1（minSpend）、Y = 1（value）；三杯 50、60、70 → 只能湊一組，送最便宜的 50
        givenPromotions(promotion(PromotionType.BUY_X_GET_Y, "1", "1"));

        BigDecimal result = promotionService.calculateBestDiscount(
                context(line(10L, 1, "70"), line(11L, 1, "50"), line(12L, 1, "60")));

        assertThat(result).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("組合優惠：依可組成套數計算折扣")
    void bundle_ShouldDiscountPerCompleteSet() {
        // 商品 10（40 元）+ 商品 20（30 元）組合價 50 元 → 每套折 20；商品 10 兩件、商品 20 一件 → 一套
        givenPromotions(promotion(PromotionType.BUNDLE_DISCOUNT, "50", null, 10L, 20L));

        BigDecimal result = promotionService.calculateBestDiscount(
                context(line(10L, 2, "40"), line(20L, 1, "30")));

        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("尚未開始或已結束的活動不套用（快取內的規則於計算時依時鐘過濾）")
    void calculateBestDiscount_ShouldIgnoreNotEffectivePromotions() {
        PromotionEntity future = promotion(PromotionType.FIXED_AMOUNT, "30", null);
        future.setStartDate(NOW.plusHours(1));
        PromotionEntity expired = promotion(PromotionType.FIXED_AMOUNT, "40", null);
        expired.setEndDate(NOW.minusMinutes(1));
        PromotionEntity current = promotion(PromotionType.FIXED_AMOUNT, "10", null);
        givenPromotions(future, expired, current);

        BigDecimal result = promotionService.calculateBestDiscount(context(line(10L, 1, "100")));

        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("促銷規則以 DTO 快取：同品牌第二次計算不再查詢資料庫")
    void getActiveRules_ShouldUseCache() {
        givenPromotions(promotion(PromotionType.FIXED_AMOUNT, "10", null, 10L));

        promotionService.calculateBestDiscount(context(line(10L, 1, "100")));
        promotionService.calculateBestDiscount(context(line(10L, 1, "100")));
        List<PromotionRule> rules = promotionService.getActiveRules(BRAND_ID);

        verify(promotionRepository, times(1)).findNotEndedByBrandWithProducts(eq(BRAND_ID), any(LocalDateTime.class));
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).applicableProductIds()).containsExactly(10L);
    }

    @Test
    @DisplayName("單一活動計算失敗或沒有計算策略時，不影響其他活動")
    void calculateBestDiscount_ShouldSkipFailingAndUnsupportedPromotions() {
        // 自訂一個必定失敗的策略（佔用 BUNDLE_DISCOUNT 類型），GIFT_WITH_PURCHASE 則沒有任何策略
        PromotionCalculator failing = new PromotionCalculator() {
            @Override
            public boolean supports(PromotionType type) {
                return type == PromotionType.BUNDLE_DISCOUNT;
            }

            @Override
            public BigDecimal calculateDiscount(PromotionContext context, PromotionRule rule) {
                throw new IllegalStateException("boom");
            }
        };
        PromotionService service = newService(List.of(failing, new FixedAmountCalculator()));
        givenPromotions(
                promotion(PromotionType.BUNDLE_DISCOUNT, "10", null, 10L),
                promotion(PromotionType.GIFT_WITH_PURCHASE, "1", null),
                promotion(PromotionType.FIXED_AMOUNT, "15", null));

        BigDecimal result = service.calculateBestDiscount(context(line(10L, 1, "100")));

        assertThat(result).isEqualByComparingTo("15.00");
    }
}
