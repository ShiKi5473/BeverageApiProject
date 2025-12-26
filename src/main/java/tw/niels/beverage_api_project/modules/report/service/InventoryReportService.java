package tw.niels.beverage_api_project.modules.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransaction;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionRepository;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariant;
import tw.niels.beverage_api_project.modules.product.entity.Recipe;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantRepository;
import tw.niels.beverage_api_project.modules.product.repository.RecipeRepository;
import tw.niels.beverage_api_project.modules.report.dto.InventoryVarianceReportDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryReportService {

    private final InventoryItemRepository itemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final RecipeRepository recipeRepository;
    private final ProductVariantRepository productVariantRepository;

    public InventoryReportService(InventoryItemRepository itemRepository,
                                  InventoryTransactionRepository transactionRepository,
                                  OrderRepository orderRepository,
                                  RecipeRepository recipeRepository,
                                  ProductVariantRepository productVariantRepository) {
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.recipeRepository = recipeRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryVarianceReportDto> generateVarianceReport(Long brandId, Long storeId, LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        // 1. 取得該品牌所有原物料
        List<InventoryItem> items = itemRepository.findByBrand_Id(brandId);

        // 2. 準備配方對照表 (Cache Recipes)
        // --- 處理 Variant Recipes ---
        List<Recipe> variantRecipes = recipeRepository.findAllVariantRecipes();
        Map<Long, List<Recipe>> variantRecipeMap = Optional.ofNullable(variantRecipes) // 1. 防止 List 本身為 null
                .orElse(Collections.emptyList())
                .stream()
                .filter(r -> r.getVariant() != null) // 2. 防止 r.getVariant() 為 null 導致 getId() 報錯
                .collect(Collectors.groupingBy(r -> r.getVariant().getId()));

        // --- 處理 Option Recipes ---
        List<Recipe> optionRecipes = recipeRepository.findAllOptionRecipes();
        Map<Long, List<Recipe>> optionRecipeMap = Optional.ofNullable(optionRecipes) // 1. 防止 List 本身為 null
                .orElse(Collections.emptyList())
                .stream()
                .filter(r -> r.getOption() != null) // 2. 防止 r.getOption() 為 null
                .collect(Collectors.groupingBy(r -> r.getOption().getId()));

        // 3. 計算理論消耗 (Theoretical Usage)
        // 撈出區間內該店所有已完成訂單
        List<Order> orders = orderRepository.findAllByBrand_IdAndStore_IdAndStatus(brandId, storeId, OrderStatus.CLOSED);
        // 過濾時間 (若 Repository 有支援 between 可直接用，這裡簡單用 stream 過濾)
        orders = orders.stream()
                .filter(o -> !o.getCompletedTime().toInstant().isBefore(startInstant) &&
                        !o.getCompletedTime().toInstant().isAfter(endInstant))
                .toList();

        // Map<ItemId, TheoreticalUsage>
        Map<Long, BigDecimal> theoreticalUsageMap = new HashMap<>();

        for (Order order : orders) {
            for (OrderItem orderItem : order.getItems()) {
                Long productId = orderItem.getProduct().getId();
                int qty = orderItem.getQuantity();

                // A. 飲品本體配方
                // 暫時解法：假設 Product 只有一個 "Default Variant"
                // 正確做法應在 OrderItem 記錄 variantId (Phase 5 優化)
                List<ProductVariant> variants = productVariantRepository.findByProduct_Brand_IdAndProduct_IdAndIsDeletedFalse(brandId, productId);
                if (!variants.isEmpty()) {
                    Long variantId = variants.getFirst().getId(); // 取第一個
                    List<Recipe> recipes = variantRecipeMap.getOrDefault(variantId, Collections.emptyList());
                    for (Recipe r : recipes) {
                        Long itemId = r.getInventoryItem().getId();
                        BigDecimal usage = r.getQuantity().multiply(BigDecimal.valueOf(qty));
                        theoreticalUsageMap.merge(itemId, usage, BigDecimal::add);
                    }
                }

                // B. 加料選項配方
                for (ProductOption opt : orderItem.getOptions()) {
                    List<Recipe> optRecipes = optionRecipeMap.getOrDefault(opt.getId(), Collections.emptyList());
                    for (Recipe r : optRecipes) {
                        Long itemId = r.getInventoryItem().getId();
                        // 加料通常一份就是一份配方量，也可能隨飲料杯數增加 (視業務邏輯，這裡假設 1 OrderItem = 1 份加料 * quantity)
                        BigDecimal usage = r.getQuantity().multiply(BigDecimal.valueOf(qty));
                        theoreticalUsageMap.merge(itemId, usage, BigDecimal::add);
                    }
                }
            }
        }

        // 4. 組合報表
        List<InventoryVarianceReportDto> report = new ArrayList<>();

        for (InventoryItem item : items) {
            Long itemId = item.getId();

            // A. 期初 (Start)
            InventoryTransaction startTx = transactionRepository
                    .findFirstByStore_IdAndInventoryItem_IdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                            storeId, itemId, startInstant)
                    .orElse(null);
            BigDecimal opening = startTx != null && startTx.getBalanceAfter() != null ?
                    startTx.getBalanceAfter() : BigDecimal.ZERO;

            // B. 期末 (End)
            InventoryTransaction endTx = transactionRepository
                    .findFirstByStore_IdAndInventoryItem_IdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                            storeId, itemId, endInstant)
                    .orElse(null);
            BigDecimal closing = endTx != null && endTx.getBalanceAfter() != null ?
                    endTx.getBalanceAfter() : BigDecimal.ZERO;

            // C. 進貨 (Restock)
            BigDecimal restock = transactionRepository.sumRestockBetween(storeId, itemId, startInstant, endInstant);
            if (restock == null) restock = BigDecimal.ZERO;

            // D. 實際消耗 = 期初 + 進貨 - 期末
            BigDecimal actualUsage = opening.add(restock).subtract(closing);
            // 若為負數 (盤盈導致)，暫時顯示 0 或保留負數

            // E. 理論消耗
            BigDecimal theoreticalUsage = theoreticalUsageMap.getOrDefault(itemId, BigDecimal.ZERO);

            // F. 差異與耗損率
            BigDecimal variance = actualUsage.subtract(theoreticalUsage);
            BigDecimal variancePct = BigDecimal.ZERO;
            if (theoreticalUsage.compareTo(BigDecimal.ZERO) > 0) {
                variancePct = variance.divide(theoreticalUsage, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            InventoryVarianceReportDto dto = new InventoryVarianceReportDto(
                    itemId,
                    item.getName(),
                    item.getUnit(),
                    opening,
                    restock,
                    closing,
                    actualUsage,
                    theoreticalUsage,
                    variance,
                    variancePct
            );
            report.add(dto);
        }

        return report;
    }
}