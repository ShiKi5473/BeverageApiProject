package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyProductStats;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStats;
import tw.niels.beverage_api_project.modules.report.repository.DailyProductStatsRepository;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReportAggregationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportAggregationService.class);

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DailyStoreStatsRepository dailyStoreStatsRepository;
    private final DailyProductStatsRepository dailyProductStatsRepository;

    public ReportAggregationService(StoreRepository storeRepository,
                                    OrderRepository orderRepository,
                                    ProductRepository productRepository,
                                    DailyStoreStatsRepository dailyStoreStatsRepository,
                                    DailyProductStatsRepository dailyProductStatsRepository) {
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.dailyProductStatsRepository = dailyProductStatsRepository;
    }

    /**
     * 執行指定日期的報表結算 (針對所有分店)
     * 通常由排程器 (Scheduler) 呼叫，傳入 "昨天" 的日期。
     */
    @Transactional
    public void generateDailyStats(LocalDate date) {
        logger.info("開始執行日結報表計算，目標日期: {}", date);

        // 1. 設定當天的時間範圍 (00:00:00 ~ 23:59:59.999999)
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 2. 找出所有「啟用中」的分店 (如果是大型系統，建議改用分頁查詢)
        // 這裡假設 StoreRepository 有 findAll，或您可以自訂 findByIsActiveTrue()
        List<Store> stores = storeRepository.findAll();

        int successCount = 0;
        int failCount = 0;

        for (Store store : stores) {
            try {
                processStoreStats(store, date, startOfDay, endOfDay);
                successCount++;
            } catch (Exception e) {
                logger.error("分店 ID: {} 日結失敗", store.getStoreId(), e);
                failCount++;
                // 這裡 catch 住例外是為了確保 A 店失敗不會導致 B 店也不結算
            }
        }
        logger.info("日結報表計算完成。成功: {}, 失敗: {}", successCount, failCount);
    }

    /**
     * 處理單一分店的結算邏輯
     */
    private void processStoreStats(Store store, LocalDate date, LocalDateTime start, LocalDateTime end) {
        Long storeId = store.getStoreId();
        Long brandId = store.getBrand().getBrandId();

        // --- 步驟 0: 冪等性檢查 (Idempotency) ---
        // 如果該店該日已經有資料，先刪除舊資料以進行重算 (Re-run)
        if (dailyStoreStatsRepository.existsByStoreIdAndDate(storeId, date)) {
            logger.debug("分店 {} 在 {} 的報表已存在，正在刪除舊資料...", storeId, date);
            dailyStoreStatsRepository.deleteByStoreIdAndDate(storeId, date);
            dailyProductStatsRepository.deleteByStoreIdAndDate(storeId, date);
            // 確保刪除操作在寫入前完成 flush (視情況需要)
        }

        // --- 步驟 1: 準備分店統計物件 ---
        DailyStoreStats storeStats = new DailyStoreStats();
        storeStats.setStoreId(storeId);
        storeStats.setBrandId(brandId);
        storeStats.setDate(date);

        // --- 步驟 2: 查詢並統計訂單狀態 (使用 DTO) ---
        List<OrderStatusStatsDto> statusStats = orderRepository.findOrderStatusStatsByStoreAndDateRange(storeId, start, end);

        for (OrderStatusStatsDto dto : statusStats) {
            if (dto.getStatus() == OrderStatus.CLOSED) {
                // 只有 CLOSED 的訂單才算入營收
                storeStats.setTotalOrders(dto.getCount().intValue());
                storeStats.setTotalRevenue(dto.getTotalAmount());
                storeStats.setTotalDiscount(dto.getDiscountAmount());
                storeStats.setFinalRevenue(dto.getFinalAmount());
            } else if (dto.getStatus() == OrderStatus.CANCELLED) {
                // 統計取消單數
                storeStats.setCancelledOrders(dto.getCount().intValue());
            }
            // 其他狀態 (如 PENDING, PREPARING) 在日結時通常忽略，或視為未完成
        }

        // --- 步驟 3: 查詢並統計付款方式 (使用 DTO) ---
        List<PaymentStatsDto> paymentStats = orderRepository.findPaymentStatsByStoreAndDateRange(storeId, start, end);

        for (PaymentStatsDto dto : paymentStats) {
            String code = dto.getPaymentCode();
            BigDecimal amount = dto.getTotalAmount();

            // 根據 Payment Code 對應到 Entity 欄位
            // 如果未來支付方式變多，建議改用 Map<String, BigDecimal> 轉 JSON 存入資料庫
            if ("CASH".equalsIgnoreCase(code)) {
                storeStats.setCashTotal(amount);
            } else if ("LINE_PAY".equalsIgnoreCase(code)) {
                storeStats.setLinePayTotal(amount);
            }
            // 其他支付方式可在此擴充
        }

        // 儲存 DailyStoreStats
        dailyStoreStatsRepository.save(storeStats);


        // --- 步驟 4: 計算並儲存商品銷售統計 (使用 DTO) ---
        List<ProductSalesStatsDto> productStats = orderRepository.findProductStatsByStoreAndDateRange(storeId, start, end);

        for (ProductSalesStatsDto dto : productStats) {
            DailyProductStats prodStat = new DailyProductStats();
            prodStat.setStoreId(storeId);
            prodStat.setBrandId(brandId);
            prodStat.setProductId(dto.getProductId());
            prodStat.setProductName(dto.getProductName()); // 快照：使用 DTO 查出的當時名稱 (或可改查 Product 表)
            prodStat.setDate(date);
            prodStat.setQuantitySold(dto.getTotalQuantity().intValue());
            prodStat.setTotalSalesAmount(dto.getTotalSalesAmount());

            // 補上分類名稱 (Category Name)
            // 因為 OrderItem 沒直接存分類，我們需要回查 Product 表
            // 注意：這會導致 N+1 查詢，但因為是批次作業 (Batch Job)，且商品數通常有限，效能可接受
            Optional<Product> productOpt = productRepository.findById(dto.getProductId());
            if (productOpt.isPresent() && !productOpt.get().getCategories().isEmpty()) {
                // 取第一個分類名稱當作報表分類
                String categoryName = productOpt.get().getCategories().iterator().next().getName();
                prodStat.setCategoryName(categoryName);
            } else {
                prodStat.setCategoryName("未分類");
            }

            dailyProductStatsRepository.save(prodStat);
        }
    }
}