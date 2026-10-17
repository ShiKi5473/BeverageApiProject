package tw.niels.beverage_api_project.modules.promotion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.promotion.entity.PromotionEntity;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

/**
 * 促銷規則快照（新增，B-3）。
 * <p>
 * 原本 PromotionService 把 {@link PromotionEntity}（含延遲載入的 applicableProducts 代理）直接存進 Redis，
 * 有序列化／反序列化與 LazyInitializationException 的風險，也讓促銷計算依賴 JPA Entity。
 * 現改為只快取這個不可變的純資料 record：
 * </p>
 * <ul>
 *   <li>適用商品只保留 ID（{@link #applicableProductIds}），不再帶整個 ProductEntity。</li>
 *   <li>集合一律複製成可變的 {@link HashSet}：Redis 使用 GenericJackson2JsonRedisSerializer（含型別資訊），
 *       {@code Set.of()} 之類的不可變集合型別無法被 Jackson 反序列化。</li>
 *   <li>保留 startDate／endDate，計算時再依當下時間判斷是否生效（快取期間活動開始或結束都能正確反映）。</li>
 * </ul>
 * <p>
 * 注意：這個 record 不可新增以 get／is 開頭的無參數方法，否則 Jackson 會把它當成額外屬性序列化，
 * 反序列化時因為沒有對應的建構子參數而失敗。
 * </p>
 *
 * @param id                   促銷活動 ID
 * @param name                 活動名稱（僅供日誌）
 * @param type                 促銷類型
 * @param value                折扣數值（金額、折數、贈送數或組合價，依類型而定）
 * @param minSpend             最低消費門檻（BUY_X_GET_Y 時代表需購買數 X）
 * @param startDate            開始時間
 * @param endDate              結束時間
 * @param applicableProductIds 適用商品 ID（空集合代表全館適用）
 */
public record PromotionRule(
        Long id,
        String name,
        PromotionType type,
        BigDecimal value,
        BigDecimal minSpend,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Set<Long> applicableProductIds) {

    public PromotionRule {
        // 防禦性複製成可變 HashSet（理由見類別說明），null 視為全館適用
        applicableProductIds = applicableProductIds != null
                ? new HashSet<>(applicableProductIds)
                : new HashSet<>();
    }

    /**
     * 由 JPA Entity 建立規則快照。
     * 呼叫端必須確保 applicableProducts 已載入（例如使用 JOIN FETCH 查詢或在交易內呼叫）。
     */
    public static PromotionRule fromEntity(PromotionEntity entity) {
        Set<Long> productIds = new HashSet<>();
        if (entity.getApplicableProducts() != null) {
            for (ProductEntity product : entity.getApplicableProducts()) {
                productIds.add(product.getId());
            }
        }
        return new PromotionRule(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getValue(),
                entity.getMinSpend(),
                entity.getStartDate(),
                entity.getEndDate(),
                productIds);
    }

    /** 是否為全館適用（沒有指定商品） */
    public boolean appliesToAllProducts() {
        return applicableProductIds.isEmpty();
    }

    /** 指定商品是否適用此活動（全館活動對任何商品都適用） */
    public boolean coversProduct(Long productId) {
        return productId != null && (appliesToAllProducts() || applicableProductIds.contains(productId));
    }

    /** 指定時間點活動是否生效（含頭含尾） */
    public boolean effectiveAt(LocalDateTime now) {
        return (startDate == null || !now.isBefore(startDate))
                && (endDate == null || !now.isAfter(endDate));
    }
}
