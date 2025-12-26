package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BuyXGetYCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.BUY_X_GET_Y;
    }

    @Override
    public BigDecimal calculateDiscount(Order order, Promotion promotion) {
        // 1. 解析 X (需購買數) 和 Y (贈送數)
        int buyCount = promotion.getMinSpend() != null ? promotion.getMinSpend().intValue() : 1;
        int freeCount = promotion.getValue() != null ? promotion.getValue().intValue() : 1;
        int groupSize = buyCount + freeCount;

        if (groupSize <= 0) return BigDecimal.ZERO;

        // 2. 取得符合資格的商品單價列表 (已抽離方法)
        List<BigDecimal> eligibleItemPrices = collectEligibleItemPrices(order, promotion);

        // 3. 計算折扣
        return calculateGroupDiscount(eligibleItemPrices, groupSize, freeCount);
    }

    /**
     * 從訂單中篩選出適用於此活動的商品，並將每個數量展開為獨立的單價
     * @param order     訂單
     * @param promotion 促銷活動設定
     * @return 符合資格的單價列表 (未排序)
     */
    private List<BigDecimal> collectEligibleItemPrices(Order order, Promotion promotion) {
        // 找出適用此活動的 Product IDs
        Set<Long> applicableProductIds = promotion.getApplicableProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        List<BigDecimal> prices = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            // 如果活動沒指定商品 (全館適用) 或者 商品在適用列表中
            // 注意：這裡假設活動是綁定 Product 層級，若是綁定 Variant 層級需修改此處邏輯
            boolean isApplicable = applicableProductIds.isEmpty() ||
                    applicableProductIds.contains(item.getProduct().getId());

            if (isApplicable) {
                // 將每個單品 (考慮數量) 的單價加入列表
                // 例如：珍珠奶茶 x 3 (單價 50)，則加入 [50, 50, 50]
                // 這裡的 getUnitPrice() 應該已經包含了 Variant 的價格邏輯
                for (int i = 0; i < item.getQuantity(); i++) {
                    prices.add(item.getUnitPrice());
                }
            }
        }
        return prices;
    }

    /**
     * 計算分組優惠折扣 (買 X 送 Y 核心邏輯)
     * @param prices    符合資格的商品價格列表
     * @param groupSize 每組大小 (X + Y)
     * @param freeCount 每組贈送數量 (Y)
     * @return 總折扣金額
     */
    private BigDecimal calculateGroupDiscount(List<BigDecimal> prices, int groupSize, int freeCount) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 1. 排序：由低到高 (因為通常是送最低價的)
        prices.sort(Comparator.naturalOrder());

        BigDecimal totalDiscount = BigDecimal.ZERO;
        int totalItems = prices.size();

        // 2. 計算可以湊成幾組
        int sets = totalItems / groupSize;

        // 3. 取前 (sets * freeCount) 個最低價的商品總和作為折扣
        int freeItemsCount = sets * freeCount;

        for (int i = 0; i < freeItemsCount; i++) {
            totalDiscount = totalDiscount.add(prices.get(i));
        }

        return totalDiscount;
    }
}