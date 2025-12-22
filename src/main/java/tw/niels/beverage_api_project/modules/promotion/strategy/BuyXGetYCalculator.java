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
        // 假設 minSpend 存 X (例如 2), value 存 Y (例如 1)
        int buyCount = promotion.getMinSpend() != null ? promotion.getMinSpend().intValue() : 1;
        int freeCount = promotion.getValue() != null ? promotion.getValue().intValue() : 1;
        int groupSize = buyCount + freeCount;

        if (groupSize <= 0) return BigDecimal.ZERO;

        // 2. 找出適用此活動的所有訂單品項
        Set<Long> applicableProductIds = promotion.getApplicableProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        List<BigDecimal> eligibleItemPrices = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            // 如果活動沒指定商品 (全館適用) 或者 商品在適用列表中
            boolean isApplicable = applicableProductIds.isEmpty() ||
                    applicableProductIds.contains(item.getProduct().getId());

            if (isApplicable) {
                // 將每個單品 (考慮數量) 的單價加入列表
                // 例如：珍珠奶茶 x 3 (單價 50)，則加入 [50, 50, 50]
                for (int i = 0; i < item.getQuantity(); i++) {
                    eligibleItemPrices.add(item.getUnitPrice());
                }
            }
        }

        // 3. 計算折扣
        // 排序：由低到高 (因為通常是送最低價的)
        eligibleItemPrices.sort(Comparator.naturalOrder());

        BigDecimal totalDiscount = BigDecimal.ZERO;
        int totalItems = eligibleItemPrices.size();

        // 每湊滿 groupSize 個，最便宜的前 freeCount 個就是贈品
        // 例如：買2送1 (組=3)，有 5 個商品 [10, 20, 30, 40, 50]
        // 第一組 (取最大的3個? 不，通常是不分組，總數中便宜的免費)
        // 邏輯修正：每買 X+Y 個，就可以減免 Y 個最低價的金額。
        // 例如買2送1，買了3個，最便宜的1個免費。買了6個，最便宜的2個免費。

        int sets = totalItems / groupSize;

        // 取前 (sets * freeCount) 個最低價的商品總和作為折扣
        int freeItemsCount = sets * freeCount;

        for (int i = 0; i < freeItemsCount; i++) {
            totalDiscount = totalDiscount.add(eligibleItemPrices.get(i));
        }

        return totalDiscount;
    }
}