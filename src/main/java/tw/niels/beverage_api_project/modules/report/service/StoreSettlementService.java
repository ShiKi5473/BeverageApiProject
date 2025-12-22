package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 分店日結結算服務。
 * <p>
 * 專門負責單一分店的數據統計與儲存，與聚合流程解耦。
 * </p>
 */
@Service
public class StoreSettlementService {

    private static final Logger logger = LoggerFactory.getLogger(StoreSettlementService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DailyStoreStatsRepository dailyStoreStatsRepository;
    private final DailyProductStatsRepository dailyProductStatsRepository;

    public StoreSettlementService(OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  DailyStoreStatsRepository dailyStoreStatsRepository,
                                  DailyProductStatsRepository dailyProductStatsRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.dailyProductStatsRepository = dailyProductStatsRepository;
    }

    /**
     * 處理單一分店的結算邏輯 (獨立交易)
     * <p>
     * 使用 REQUIRES_NEW 傳播屬性，強制為每一次呼叫開啟一個全新的交易。
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processStoreStats(Long storeId, Long brandId, LocalDate date, LocalDateTime start, LocalDateTime end) {

        // --- 步驟 0: 冪等性檢查 ---
        if (dailyStoreStatsRepository.existsByStoreIdAndDate(storeId, date)) {
            logger.debug("分店 {} 在 {} 的報表已存在，正在刪除舊資料...", storeId, date);
            dailyStoreStatsRepository.deleteByStoreIdAndDate(storeId, date);
            dailyProductStatsRepository.deleteByStoreIdAndDate(storeId, date);
            dailyStoreStatsRepository.flush();
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
                logger.warn("【日結警告】分店 ID: {} 在日期 {} 尚有 {} 筆訂單未結案 (非 CLOSED/CANCELLED)。",
                        storeId, date, unclosedCount);
            }

            dailyProductStatsRepository.save(prodStat);
        }
    }
}