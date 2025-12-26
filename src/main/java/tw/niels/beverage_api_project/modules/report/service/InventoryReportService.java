package tw.niels.beverage_api_project.modules.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
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

    public InventoryReportService(InventoryItemRepository itemRepository,
                                  InventoryTransactionRepository transactionRepository,
                                  OrderRepository orderRepository,
                                  RecipeRepository recipeRepository) {
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.recipeRepository = recipeRepository;
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
        Map<Long, List<Recipe>> variantRecipeMap = Optional.ofNullable(variantRecipes)
                .orElse(Collections.emptyList())
                .stream()
                // 【修正】增加 ID 非空檢查，解決 groupingBy 不允許 null key 的問題
                .filter(r -> r.getVariant() != null && r.getVariant().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getVariant().getId()));

        // --- 處理 Option Recipes ---
        List<Recipe> optionRecipes = recipeRepository.findAllOptionRecipes();
        Map<Long, List<Recipe>> optionRecipeMap = Optional.ofNullable(optionRecipes)
                .orElse(Collections.emptyList())
                .stream()
                // 【修正】增加 ID 非空檢查
                .filter(r -> r.getOption() != null && r.getOption().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getOption().getId()));

        // 3. 計算理論消耗 (Theoretical Usage)
        // 撈出區間內該店所有已完成訂單
        List<Order> orders = orderRepository.findAllByBrand_IdAndStore_IdAndStatus(brandId, storeId, OrderStatus.CLOSED);

        orders = orders.stream()
                .filter(o -> !o.getCompletedTime().toInstant().isBefore(startInstant) &&
                        !o.getCompletedTime().toInstant().isAfter(endInstant))
                .toList();

        // Map<ItemId, TheoreticalUsage>
        Map<Long, BigDecimal> theoreticalUsageMap = new HashMap<>();

        for (Order order : orders) {
            for (OrderItem orderItem : order.getItems()) {
                int qty = orderItem.getQuantity();

                // A. 飲品本體配方 (已修正：支援規格)
                // 從 OrderItem 直接取得 ProductVariant，不再猜測
                ProductVariant variant = orderItem.getProductVariant();

                if (variant != null && variant.getId() != null) {
                    Long variantId = variant.getId();
                    // 根據規格 ID 抓取對應配方 (例如：大杯配方)
                    List<Recipe> recipes = variantRecipeMap.getOrDefault(variantId, Collections.emptyList());

                    for (Recipe r : recipes) {
                        if (r.getInventoryItem() != null) {
                            Long itemId = r.getInventoryItem().getId();
                            BigDecimal usage = r.getQuantity().multiply(BigDecimal.valueOf(qty));
                            theoreticalUsageMap.merge(itemId, usage, BigDecimal::add);
                        }
                    }
                } else {
                    // 當訂單已完成卻沒有規格資訊，視為資料嚴重異常，拋出 Exception 中斷報表
                    throw new ResourceNotFoundException(String.format(
                            "庫存報表生成失敗：訂單號碼 %s 的品項 (ID: %d, 商品: %s) 缺少規格(Variant)資訊，無法計算配方消耗。",
                            order.getOrderNumber(),
                            orderItem.getId(),
                            orderItem.getProduct() != null ? orderItem.getProduct().getName() : "Unknown"
                    ));
                }

                // B. 加料選項配方
                if (orderItem.getOptions() != null) {
                    for (ProductOption opt : orderItem.getOptions()) {
                        List<Recipe> optRecipes = optionRecipeMap.getOrDefault(opt.getId(), Collections.emptyList());
                        for (Recipe r : optRecipes) {
                            if (r.getInventoryItem() != null) {
                                Long itemId = r.getInventoryItem().getId();
                                BigDecimal usage = r.getQuantity().multiply(BigDecimal.valueOf(qty));
                                theoreticalUsageMap.merge(itemId, usage, BigDecimal::add);
                            }
                        }
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