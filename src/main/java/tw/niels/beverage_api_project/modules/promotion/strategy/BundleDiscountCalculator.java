package tw.niels.beverage_api_project.modules.promotion.strategy;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.promotion.entity.Promotion;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BundleDiscountCalculator implements PromotionCalculator {

    @Override
    public boolean supports(PromotionType type) {
        return type == PromotionType.BUNDLE_DISCOUNT;
    }

    @Override
    public BigDecimal calculateDiscount(Order order, Promotion promotion) {
        Set<Long> requiredProductIds = promotion.getApplicableProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        if (requiredProductIds.isEmpty()) return BigDecimal.ZERO;

        // 統計訂單中各商品的數量與單價
        Map<Long, Integer> cartCounts = new HashMap<>();
        Map<Long, BigDecimal> cartPrices = new HashMap<>();

        for (OrderItem item : order.getItems()) {
            Long pid = item.getProduct().getId();
            cartCounts.put(pid, cartCounts.getOrDefault(pid, 0) + item.getQuantity());
            // 簡化：假設同商品單價相同 (忽略選項差異，或者取第一個)
            cartPrices.putIfAbsent(pid, item.getUnitPrice());
        }

        // 計算可以組成幾套組合
        int possibleSets = Integer.MAX_VALUE;
        BigDecimal originalBundlePrice = BigDecimal.ZERO;

        for (Long pid : requiredProductIds) {
            int count = cartCounts.getOrDefault(pid, 0);
            if (count == 0) return BigDecimal.ZERO; // 缺件，無法組成
            possibleSets = Math.min(possibleSets, count);

            // 累加原價
            originalBundlePrice = originalBundlePrice.add(cartPrices.get(pid));
        }

        // 計算折扣
        // 優惠價 (value) 是「整組」的價格
        // 單組折扣 = 原價總和 - 優惠價
        BigDecimal bundleDiscount = originalBundlePrice.subtract(promotion.getValue());

        if (bundleDiscount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO; // 優惠價比原價還貴，不折抵

        return bundleDiscount.multiply(new BigDecimal(possibleSets));
    }
}