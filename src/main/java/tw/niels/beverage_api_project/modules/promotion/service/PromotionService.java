package tw.niels.beverage_api_project.modules.promotion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.dto.CreatePromotionRequestDto;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionResponseDto;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionRule;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;
import tw.niels.beverage_api_project.modules.promotion.repository.PromotionJpaRepository;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionCalculator;
import tw.niels.beverage_api_project.modules.promotion.strategy.PromotionStrategyFactory;

/**
 * 促銷活動服務
 * <p>
 * 修改說明（B-3）：
 * <ul>
 *   <li>新增 {@link #calculateBestDiscount(PromotionContext)}（取代舊的 OrderEntity 版本，已移除），
 *       由 Order BC 以訂單品項建立 {@link PromotionContext} 後呼叫，結帳與金額試算共用。</li>
 *   <li>快取改存 {@link PromotionRule} DTO（不再快取含延遲載入代理的 JPA Entity），
 *       快取名稱改為 {@value #RULE_CACHE}，避免讀到舊版存入、型別不同的 "promotions" 快取內容。</li>
 *   <li>刪除重複的 PromotionCacheService：原本它與本類別各自在同一個 "promotions" 快取上加了 @Cacheable。
 *       為了避免 self-invocation 讓 @Cacheable 失效，改為在本類別內透過 {@link CacheManager} 讀寫快取。</li>
 *   <li>活動是否生效改用注入的 {@link Clock}（營業時區）判斷，不再依賴 JVM 預設時區。</li>
 *   <li>欄位、區域變數原與型別同名（批次改名殘留），改為小寫駝峰命名。</li>
 *   <li>對外方法改回傳 DTO 並在交易內轉換，Controller 不再接觸 JPA Entity。</li>
 * </ul>
 * </p>
 */
@Service
public class PromotionService {

    private static final Logger logger = LoggerFactory.getLogger(PromotionService.class);

    /** 有效（尚未結束）促銷規則的快取名稱，value 為 List&lt;PromotionRule&gt;，key 為 brandId */
    public static final String RULE_CACHE = "promotion-rules";

    private final PromotionJpaRepository promotionJpaRepository;
    private final PromotionStrategyFactory strategyFactory;
    private final BrandJpaRepository brandRepository;
    private final ProductJpaRepository productRepository;
    private final CacheManager cacheManager;
    private final Clock clock;

    public PromotionService(PromotionJpaRepository promotionJpaRepository,
                            PromotionStrategyFactory strategyFactory,
                            BrandJpaRepository brandRepository,
                            ProductJpaRepository productRepository,
                            CacheManager cacheManager,
                            Clock clock) {
        this.promotionJpaRepository = promotionJpaRepository;
        this.strategyFactory = strategyFactory;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    /**
     * 計算訂單的最佳促銷折扣（多個活動不疊加，取折扣最大者）。
     * <p>
     * 保證：回傳值介於 0 與 {@code ctx.totalAmount()} 之間（含），小數兩位。
     * 個別促銷計算失敗時不中斷整體流程，收集後以單一日誌輸出彙總（M8）。
     * </p>
     *
     * @param ctx 訂單品項與總額（由 Order BC 建立）
     * @return 最佳折扣金額
     */
    public BigDecimal calculateBestDiscount(PromotionContext ctx) {
        // 沒有品牌或總額 <= 0 時不可能有折扣
        if (ctx == null || ctx.brandId() == null || ctx.totalAmount() == null
                || ctx.totalAmount().signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Long brandId = ctx.brandId();
        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal maxDiscount = BigDecimal.ZERO;

        // 收集計算失敗的活動（M8：累積後統一輸出，避免每筆失敗都呼叫 logger）
        List<String> failedPromotions = new ArrayList<>();

        for (PromotionRule rule : getActiveRules(brandId)) {
            // 快取內含尚未開始的活動，計算時再依當下時間過濾
            if (!rule.effectiveAt(now)) {
                continue;
            }
            // 沒有計算策略的類型（例如 GIFT_WITH_PURCHASE 滿額贈）不影響金額，直接略過
            Optional<PromotionCalculator> calculator = strategyFactory.findCalculator(rule.type());
            if (calculator.isEmpty()) {
                continue;
            }
            try {
                BigDecimal discount = calculator.get().calculateDiscount(ctx, rule);
                if (discount != null && discount.compareTo(maxDiscount) > 0) {
                    maxDiscount = discount;
                }
            } catch (RuntimeException e) {
                // 個別促銷計算失敗不應中斷整體折扣計算，收集後統一彙報
                failedPromotions.add(String.format("'%s'(ID:%d):%s", rule.name(), rule.id(), e.getMessage()));
            }
        }

        // 統一輸出失敗彙總（而非每次失敗各自輸出一行）
        if (!failedPromotions.isEmpty()) {
            logger.warn("品牌 ID={} 共 {} 個促銷活動折扣計算失敗，已跳過：{}",
                    brandId, failedPromotions.size(), String.join("; ", failedPromotions));
        }

        // 折扣不可超過訂單總額（maxDiscount 起始為 0，因此也不會是負數）
        if (maxDiscount.compareTo(ctx.totalAmount()) > 0) {
            maxDiscount = ctx.totalAmount();
        }
        return maxDiscount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 取得品牌已啟用且尚未結束的促銷規則（含尚未開始者），優先讀取快取。
     * <p>
     * 不使用 @Cacheable：本方法由 {@link #calculateBestDiscount} 在同一個 Bean 內呼叫，
     * 經過代理的註解在 self-invocation 時不會生效，因此直接透過 CacheManager 讀寫。
     * 快取讀寫失敗（例如 Redis 暫時無法連線）時退回查詢資料庫，不讓結帳因快取問題失敗。
     * </p>
     */
    public List<PromotionRule> getActiveRules(Long brandId) {
        Cache cache = cacheManager.getCache(RULE_CACHE);
        if (cache != null) {
            try {
                List<PromotionRule> cached = readCachedRules(cache.get(brandId));
                if (cached != null) {
                    return cached;
                }
            } catch (RuntimeException e) {
                logger.warn("讀取促銷規則快取失敗（品牌 ID={}），改查資料庫：{}", brandId, e.getMessage());
            }
        }

        List<PromotionRule> rules = loadActiveRules(brandId);

        if (cache != null) {
            try {
                cache.put(brandId, rules);
            } catch (RuntimeException e) {
                logger.warn("寫入促銷規則快取失敗（品牌 ID={}）：{}", brandId, e.getMessage());
            }
        }
        return rules;
    }

    /**
     * 解析快取內容；不存在或內容型別不符（例如舊版資料）時回傳 null，由呼叫端重新查詢。
     */
    private List<PromotionRule> readCachedRules(Cache.ValueWrapper wrapper) {
        if (wrapper == null || !(wrapper.get() instanceof List<?> cachedList)) {
            return null;
        }
        List<PromotionRule> rules = new ArrayList<>(cachedList.size());
        for (Object item : cachedList) {
            if (!(item instanceof PromotionRule rule)) {
                return null;
            }
            rules.add(rule);
        }
        return rules;
    }

    /**
     * 從資料庫載入規則並轉成 DTO。
     * 查詢已 JOIN FETCH 適用商品，轉換不需要額外交易；回傳可變的 ArrayList 以便 Redis 正確反序列化。
     */
    private List<PromotionRule> loadActiveRules(Long brandId) {
        return promotionJpaRepository.findNotEndedByBrandWithProducts(brandId, LocalDateTime.now(clock))
                .stream()
                .map(PromotionRule::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 建立新促銷活動
     */
    @CacheEvict(value = RULE_CACHE, key = "#brandId") // 清除該品牌的促銷規則快取
    @Transactional
    public PromotionResponseDto createPromotion(Long brandId, CreatePromotionRequestDto request) {
        // 修改：區域變數原與型別同名（BrandEntity BrandEntity），改為 brand
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌 ID: " + brandId));

        // 修改：DB 有 CHECK (end_date > start_date)，相等時也要擋下（原本只擋 isBefore，相等會變成 DB 例外）
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BadRequestException("結束時間必須晚於開始時間");
        }
        validateRuleValue(request);

        PromotionEntity promotion = new PromotionEntity();
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
            // 修復 M2：以批次查詢取代迴圈內的 N+1 個別查詢
            List<ProductEntity> foundProducts = productRepository.findByBrand_IdAndIdIn(
                    brandId, request.getApplicableProductIds());

            // 驗證所有指定商品 ID 都存在且屬於此品牌
            if (foundProducts.size() != request.getApplicableProductIds().size()) {
                Set<Long> foundIds = foundProducts.stream()
                        .map(ProductEntity::getId)
                        .collect(Collectors.toSet());
                List<Long> invalidIds = request.getApplicableProductIds().stream()
                        .filter(pid -> !foundIds.contains(pid))
                        .toList();
                throw new BadRequestException("以下商品 ID 無效或不屬於此品牌：" + invalidIds);
            }

            promotion.setApplicableProducts(new HashSet<>(foundProducts));
        }

        // 修改：在交易內轉成 DTO 再回傳
        return PromotionResponseDto.fromEntity(promotionJpaRepository.save(promotion));
    }

    /**
     * 新增：依促銷類型檢查折扣數值，避免建立出「永遠算不出折扣」的活動。
     */
    private void validateRuleValue(CreatePromotionRequestDto request) {
        if (request.getType() == PromotionType.PERCENTAGE
                && request.getValue() != null
                && request.getValue().compareTo(BigDecimal.ONE) >= 0) {
            throw new BadRequestException("百分比折扣的折數必須介於 0 與 1 之間（例如 0.85 代表 85 折）");
        }
        if (request.getType() == PromotionType.BUNDLE_DISCOUNT
                && (request.getApplicableProductIds() == null || request.getApplicableProductIds().isEmpty())) {
            throw new BadRequestException("組合優惠必須指定適用商品");
        }
    }

    /**
     * 查詢品牌的所有活動
     * 修改：改回傳 DTO（一併載入適用商品，避免 Controller 在交易外觸發延遲載入與 N+1）
     */
    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getPromotionsByBrand(Long brandId) {
        return promotionJpaRepository.findWithProductsByBrand_Id(brandId).stream()
                .map(PromotionResponseDto::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }


    /**
     * 關閉/刪除活動
     */
    @CacheEvict(value = RULE_CACHE, key = "#brandId") // 清除該品牌的促銷規則快取
    @Transactional
    public void deactivatePromotion(Long brandId, Long promotionId) {
        PromotionEntity promotion = promotionJpaRepository.findByBrand_IdAndId(brandId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到促銷活動 ID: " + promotionId));

        promotion.setIsActive(false);
        promotionJpaRepository.save(promotion);
    }
}
