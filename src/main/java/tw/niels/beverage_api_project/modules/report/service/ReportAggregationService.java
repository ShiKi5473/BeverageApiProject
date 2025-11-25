package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // 新增
import org.springframework.context.annotation.Lazy;           // 新增
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation; // 新增
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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

/**
 * 報表聚合核心服務。
 * <p>
 * 負責執行每日的數據結算邏輯。
 * 採用「分頁批次」與「獨立交易」模式，確保大量分店結算時的效能與容錯性。
 * </p>
 */
@Service
public class ReportAggregationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportAggregationService.class);

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DailyStoreStatsRepository dailyStoreStatsRepository;
    private final DailyProductStatsRepository dailyProductStatsRepository;

    /**
     * 【自我注入 (Self-Injection)】
     * 用於在類別內部呼叫自身的 @Transactional 方法時，能正確觸發 AOP 代理。
     * 使用 @Lazy 避免循環依賴 (Circular Dependency)。
     */
    @Autowired
    @Lazy
    private ReportAggregationService self;

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
     * 執行指定日期的報表結算 (分頁批次處理)
     * <p>
     * 此方法本身 **不開啟** 交易，而是讓內部迴圈的每一筆處理獨立開啟交易。
     * 這樣可避免長交易 (Long Transaction) 鎖死資料庫資源，並實現「部分成功」的容錯機制。
     * </p>
     */
    // 移除 @Transactional，改在 processStoreStats 上控制
    public void generateDailyStats(LocalDate date) {
        logger.info("開始執行日結報表計算，目標日期: {}", date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        int successCount = 0;
        int failCount = 0;

        // 分頁設定
        int pageNumber = 0;
        int pageSize = 50;
        Page<Store> storePage;

        do {
            // 1. 查詢該頁分店
            storePage = storeRepository.findAllStoresForSystem(
                    PageRequest.of(pageNumber, pageSize, Sort.by("storeId"))
            );

            // 2. 處理該頁的分店
            for (Store store : storePage.getContent()) {
                try {
                    // 【修改】透過 self 代理物件呼叫，確保 @Transactional 生效
                    self.processStoreStats(store, date, startOfDay, endOfDay);
                    successCount++;
                } catch (Exception e) {
                    logger.error("分店 ID: {} 日結失敗", store.getStoreId(), e);
                    failCount++;
                    // 單一分店失敗不影響其他分店 (因為是獨立交易)
                }
            }

            // 3. 準備下一頁
            pageNumber++;

            if (pageNumber % 10 == 0) {
                logger.info("已處理 {} 頁分店...", pageNumber);
            }

        } while (storePage.hasNext());

        logger.info("日結報表計算完成。成功: {}, 失敗: {}, 總分店數: {}", successCount, failCount, storePage.getTotalElements());
    }

    /**
     * 處理單一分店的結算邏輯 (獨立交易)
     * <p>
     * 使用 REQUIRES_NEW 傳播屬性，強制為每一次呼叫開啟一個全新的交易。
     * 執行結束後立即 Commit，若發生例外則單獨 Rollback。
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 新增交易註解與傳播屬性
    public void processStoreStats(Store store, LocalDate date, LocalDateTime start, LocalDateTime end) { // 【修改】改為 public
        Long storeId = store.getStoreId();
        Long brandId = store.getBrand().getBrandId();

        // --- 步驟 0: 冪等性檢查 ---
        if (dailyStoreStatsRepository.existsByStoreIdAndDate(storeId, date)) {
            logger.debug("分店 {} 在 {} 的報表已存在，正在刪除舊資料...", storeId, date);
            dailyStoreStatsRepository.deleteByStoreIdAndDate(storeId, date);
            dailyProductStatsRepository.deleteByStoreIdAndDate(storeId, date);
            dailyStoreStatsRepository.flush(); // 確保刪除操作在同一交易內生效
        }

        // --- 步驟 1: 準備分店統計物件 ---
        DailyStoreStats storeStats = new DailyStoreStats();
        storeStats.setStoreId(storeId);
        storeStats.setBrandId(brandId);
        storeStats.setDate(date);

        // --- 步驟 2: 查詢並統計訂單狀態 ---
        List<OrderStatusStatsDto> statusStats = orderRepository.findOrderStatusStatsByStoreAndDateRange(storeId, start, end);

        for (OrderStatusStatsDto dto : statusStats) {
            if (dto.getStatus() == OrderStatus.CLOSED) {
                storeStats.setTotalOrders(dto.getCount().intValue());
                storeStats.setTotalRevenue(dto.getTotalAmount());
                storeStats.setTotalDiscount(dto.getDiscountAmount());
                storeStats.setFinalRevenue(dto.getFinalAmount());
            } else if (dto.getStatus() == OrderStatus.CANCELLED) {
                storeStats.setCancelledOrders(dto.getCount().intValue());
            }
        }

        // --- 步驟 3: 查詢並統計付款方式 ---
        List<PaymentStatsDto> paymentStats = orderRepository.findPaymentStatsByStoreAndDateRange(storeId, start, end);

        for (PaymentStatsDto dto : paymentStats) {
            String code = dto.getPaymentCode();
            BigDecimal amount = dto.getTotalAmount();

            if ("CASH".equalsIgnoreCase(code)) {
                storeStats.setCashTotal(amount);
            } else if ("LINE_PAY".equalsIgnoreCase(code)) {
                storeStats.setLinePayTotal(amount);
            }
        }

        dailyStoreStatsRepository.save(storeStats);

        // --- 步驟 4: 計算並儲存商品銷售統計 ---
        List<ProductSalesStatsDto> productStats = orderRepository.findProductStatsByStoreAndDateRange(storeId, start, end);

        for (ProductSalesStatsDto dto : productStats) {
            DailyProductStats prodStat = new DailyProductStats();
            prodStat.setStoreId(storeId);
            prodStat.setBrandId(brandId);
            prodStat.setProductId(dto.getProductId());
            prodStat.setProductName(dto.getProductName());
            prodStat.setDate(date);
            prodStat.setQuantitySold(dto.getTotalQuantity().intValue());
            prodStat.setTotalSalesAmount(dto.getTotalSalesAmount());

            Optional<Product> productOpt = productRepository.findByBrand_IdAndId(brandId, dto.getProductId());

            if (productOpt.isPresent() && !productOpt.get().getCategories().isEmpty()) {
                String categoryName = productOpt.get().getCategories().iterator().next().getName();
                prodStat.setCategoryName(categoryName);
            } else {
                prodStat.setCategoryName("未分類");
            }

            Date startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());

            long unclosedCount = orderRepository.countOrdersByStoreIdAndOrderTimeBetweenAndStatusIn(
                    storeId,
                    startDate,
                    endDate,
                    Arrays.asList(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.PENDING, OrderStatus.HELD, OrderStatus.AWAITING_ACCEPTANCE)
            );

            if (unclosedCount > 0) {
                logger.warn("【日結警告】分店 ID: {} 在日期 {} 尚有 {} 筆訂單未結案 (非 CLOSED/CANCELLED)。" +
                                "這些訂單未計入本日營收報表，請確認是否為店員漏按完成。",
                        storeId, date, unclosedCount);
            }

            dailyProductStatsRepository.save(prodStat);
        }
    }
}