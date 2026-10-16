package tw.niels.beverage_api_project.modules.inventory.application.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryDeductionService;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.RecipeEntity;
import tw.niels.beverage_api_project.modules.product.repository.RecipeJpaRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Inventory
 * Layer: Application - Event Handler
 * ============================================================
 *
 * 訂單完成後庫存扣減事件監聽器
 *
 * 訂閱 {@link OrderClosedEvent}，在訂單事務提交後（AFTER_COMMIT）
 * 開啟獨立新事務，依照訂單品項的食材配方（Recipe）執行 FIFO 庫存扣減。
 *
 * 處理流程：
 * 1. 載入訂單（含品項、規格、選項）
 * 2. 批次查詢規格與選項對應的食材配方
 * 3. 在記憶體內計算每種食材的總扣減量（配方量 × 品項數量）
 * 4. 委派 {@link InventoryDeductionService#processBatchDeductions} 執行 FIFO 批次扣減
 *
 * 設計決策：
 * - 使用 AFTER_COMMIT + REQUIRES_NEW，確保庫存扣減在訂單持久化成功後才執行
 * - 若庫存不足，拋出例外記錄錯誤日誌（訂單已 CLOSED，不回滾主事務）
 * ============================================================
 */
@Component
public class OrderClosedInventoryHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderClosedInventoryHandler.class);

    private final OrderJpaRepository orderJpaRepository;
    private final RecipeJpaRepository recipeJpaRepository;
    private final InventoryDeductionService inventoryDeductionService;
    // [B-5 修正] 以程式化交易 (REQUIRES_NEW) 包住「載入訂單 + 扣庫存」，失敗時整段乾淨回滾
    private final TransactionTemplate newTransaction;

    public OrderClosedInventoryHandler(OrderJpaRepository orderJpaRepository,
                                       RecipeJpaRepository recipeJpaRepository,
                                       InventoryDeductionService inventoryDeductionService,
                                       PlatformTransactionManager transactionManager) {
        this.orderJpaRepository = orderJpaRepository;
        this.recipeJpaRepository = recipeJpaRepository;
        this.inventoryDeductionService = inventoryDeductionService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 處理訂單完成事件，執行庫存扣減。
     *
     * <p>[B-5 修正] 原本整個方法標 {@code @Transactional(REQUIRES_NEW)}，內部再呼叫同樣
     * {@code @Transactional} 的 processBatchDeductions（會加入同一個交易）。扣減失敗時，
     * 內層的例外會讓整個交易被標成 rollback-only，即使這裡 catch 住例外，方法結束提交時仍會拋
     * UnexpectedRollbackException，日誌會出現誤導的第二個錯誤。
     * 現在改成：本方法不開交易，由 {@link TransactionTemplate} 開啟獨立新交易；
     * 例外由 template 負責回滾並往外拋，這裡在交易「之外」捕捉並乾淨地記錄一次即可。</p>
     *
     * @param event 訂單完成事件，攜帶 orderId、brandId、storeId
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderClosedEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> deductForOrder(event));
        } catch (Exception e) {
            // 訂單已 CLOSED 並提交，庫存扣減失敗不影響訂單；此處只記錄一次錯誤供人工補扣
            logger.error("訂單 ID={} 庫存扣減失敗（訂單已完成，不回滾）: {}", event.orderId(), e.getMessage(), e);
        }
    }

    /**
     * 在獨立新交易內執行的實際扣減邏輯。失敗時直接拋出例外，由外層 template 回滾。
     */
    private void deductForOrder(OrderClosedEvent event) {
        // 1. 載入訂單（含品項關聯）
        OrderEntity order = orderJpaRepository.findByIdForSystem(event.orderId()).orElse(null);
        if (order == null) {
            logger.error("庫存扣減失敗：找不到訂單 ID={}", event.orderId());
            return;
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            logger.warn("訂單 ID={} 無品項，跳過庫存扣減", event.orderId());
            return;
        }

        // 2. 收集所有規格 ID 與選項 ID
        Set<Long> variantIds = order.getItems().stream()
                .filter(item -> item.getProductVariant() != null)
                .map(item -> item.getProductVariant().getId())
                .collect(Collectors.toSet());

        Set<Long> optionIds = order.getItems().stream()
                .flatMap(item -> item.getOptions().stream())
                .map(opt -> opt.getId())
                .collect(Collectors.toSet());

        // 3. 批次查詢配方（避免 N+1）
        List<RecipeEntity> variantRecipes = variantIds.isEmpty()
                ? List.of()
                : recipeJpaRepository.findByVariantIdIn(variantIds);

        List<RecipeEntity> optionRecipes = optionIds.isEmpty()
                ? List.of()
                : recipeJpaRepository.findByOptionIdIn(optionIds);

        // 4. 在記憶體內計算每種食材的總扣減量
        // deductionMap: inventoryItemId -> 總扣減量
        Map<Long, BigDecimal> deductionMap = new HashMap<>();

        for (OrderItemEntity item : order.getItems()) {
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());

            // 規格配方
            if (item.getProductVariant() != null) {
                Long variantId = item.getProductVariant().getId();
                for (RecipeEntity recipe : variantRecipes) {
                    if (recipe.getVariant() != null && variantId.equals(recipe.getVariant().getId())) {
                        Long inventoryItemId = recipe.getInventoryItem().getId();
                        BigDecimal recipeQty = recipe.getQuantity().multiply(qty);
                        deductionMap.merge(inventoryItemId, recipeQty, BigDecimal::add);
                    }
                }
            }

            // 選項配方
            for (var option : item.getOptions()) {
                for (RecipeEntity recipe : optionRecipes) {
                    if (recipe.getOption() != null && option.getId().equals(recipe.getOption().getId())) {
                        Long inventoryItemId = recipe.getInventoryItem().getId();
                        BigDecimal recipeQty = recipe.getQuantity().multiply(qty);
                        deductionMap.merge(inventoryItemId, recipeQty, BigDecimal::add);
                    }
                }
            }
        }

        if (deductionMap.isEmpty()) {
            logger.info("訂單 ID={} 無配方資料，跳過庫存扣減", event.orderId());
            return;
        }

        // 5. 委派庫存扣減服務執行 FIFO 批次扣減
        Long brandId = order.getBrand().getId();
        Long storeId = order.getStore().getId();

        // 例外不在此處吞掉：讓它傳出 template 觸發乾淨的回滾，再由 handle() 統一記錄
        inventoryDeductionService.processBatchDeductions(brandId, storeId, deductionMap);
        logger.info("訂單 ID={} 庫存扣減完成，共扣減 {} 種食材", event.orderId(), deductionMap.size());
    }
}
