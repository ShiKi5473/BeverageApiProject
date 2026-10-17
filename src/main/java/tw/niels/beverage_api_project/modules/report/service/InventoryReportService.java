package tw.niels.beverage_api_project.modules.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionJpaRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.entity.RecipeEntity;
import tw.niels.beverage_api_project.modules.product.repository.RecipeJpaRepository;
import tw.niels.beverage_api_project.modules.report.dto.InventoryVarianceReportDto;
import tw.niels.beverage_api_project.modules.report.repository.OrderReportQueryRepository;

@Service
public class InventoryReportService {

    private final InventoryItemJpaRepository itemRepository;
    private final InventoryTransactionJpaRepository transactionRepository;
    // [B-5 / 效能] 改用報表模組自己的訂單查詢（以 completedTime 篩出區間並一次抓回品項 / 規格 / 選項），
    // 不再相依訂單核心的 OrderJpaRepository，也不用先撈出該店全部歷史訂單
    private final OrderReportQueryRepository orderReportQueryRepository;
    // [命名修正] 欄位名稱原本與型別同名 (批次改名殘留)，改為 recipeRepository
    private final RecipeJpaRepository recipeRepository;
    // [B-7] 報表區間以營業時區計算，不使用 ZoneId.systemDefault() (Docker 預設 UTC 會偏移 8 小時)
    private final ZoneId businessZoneId;

    public InventoryReportService(InventoryItemJpaRepository itemRepository,
                                  InventoryTransactionJpaRepository transactionRepository,
                                  OrderReportQueryRepository orderReportQueryRepository,
                                  RecipeJpaRepository recipeRepository,
                                  ZoneId businessZoneId) {
        this.businessZoneId = businessZoneId;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.orderReportQueryRepository = orderReportQueryRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryVarianceReportDto> generateVarianceReport(Long brandId, Long storeId, LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(businessZoneId).toInstant();
        Instant endInstant = endDate.atTime(23, 59, 59).atZone(businessZoneId).toInstant();

        // 1. 取得該品牌所有原物料
        List<InventoryItemEntity> items = itemRepository.findByBrand_Id(brandId);

        // 2. 準備配方對照表 (Cache Recipes)
        // --- 處理 Variant Recipes ---
        List<RecipeEntity> variantRecipes = recipeRepository.findAllVariantRecipeEntitys();
        Map<Long, List<RecipeEntity>> variantRecipeMap = Optional.ofNullable(variantRecipes)
                .orElse(Collections.emptyList())
                .stream()
                // 【修正】增加 ID 非空檢查，解決 groupingBy 不允許 null key 的問題
                .filter(r -> r.getVariant() != null && r.getVariant().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getVariant().getId()));

        // --- 處理 Option Recipes ---
        List<RecipeEntity> optionRecipes = recipeRepository.findAllOptionRecipeEntitys();
        Map<Long, List<RecipeEntity>> optionRecipeMap = Optional.ofNullable(optionRecipes)
                .orElse(Collections.emptyList())
                .stream()
                // 【修正】增加 ID 非空檢查
                .filter(r -> r.getOption() != null && r.getOption().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getOption().getId()));

        // 3. 計算理論消耗 (Theoretical Usage)
        // 撈出區間內該店所有已完成訂單
        // [B-5 / 效能] 直接在資料庫以 completedTime 篩選區間（java.util.Date 參數），一次抓回品項、規格、選項；
        // 原本是載入該店「全部歷史」已完成訂單後才在記憶體過濾，且品項 / 規格 / 選項逐筆延遲載入。
        List<OrderEntity> orders = orderReportQueryRepository.findClosedOrdersWithItems(
                brandId, storeId, Date.from(startInstant), Date.from(endInstant));

        // Map<ItemId, TheoreticalUsage>
        Map<Long, BigDecimal> theoreticalUsageMap = new HashMap<>();

        // [命名修正] 迴圈變數原本與型別同名 (OrderEntity OrderEntity / OrderItemEntity OrderItemEntity)，改為 order / orderItem
        for (OrderEntity order : orders) {
            for (OrderItemEntity orderItem : order.getItems()) {
                int qty = orderItem.getQuantity();

                // A. 飲品本體配方 (已修正：支援規格)
                // 從 OrderItemEntity 直接取得 ProductVariantEntity，不再猜測
                ProductVariantEntity variant = orderItem.getProductVariant();

                if (variant != null && variant.getId() != null) {
                    Long variantId = variant.getId();
                    // 根據規格 ID 抓取對應配方 (例如：大杯配方)
                    List<RecipeEntity> recipes = variantRecipeMap.getOrDefault(variantId, Collections.emptyList());

                    for (RecipeEntity r : recipes) {
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
                    for (ProductOptionEntity opt : orderItem.getOptions()) {
                        List<RecipeEntity> optRecipes = optionRecipeMap.getOrDefault(opt.getId(), Collections.emptyList());
                        for (RecipeEntity r : optRecipes) {
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

        for (InventoryItemEntity item : items) {
            Long itemId = item.getId();

            // A. 期初 (Start)
            InventoryTransactionEntity startTx = transactionRepository
                    .findFirstByStore_IdAndInventoryItem_IdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                            storeId, itemId, startInstant)
                    .orElse(null);
            BigDecimal opening = startTx != null && startTx.getBalanceAfter() != null ?
                    startTx.getBalanceAfter() : BigDecimal.ZERO;

            // B. 期末 (End)
            InventoryTransactionEntity endTx = transactionRepository
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