package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.report.dto.OrderStatusStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.PaymentStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyProductStatsEntity;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStatsEntity;
import tw.niels.beverage_api_project.modules.report.repository.DailyProductStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.repository.OrderReportQueryRepository;
import tw.niels.beverage_api_project.modules.report.repository.ProductCategoryReportQueryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分店日結結算服務。
 * <p>
 * 專門負責單一分店的數據統計與儲存，與聚合流程解耦。
 * </p>
 */
@Service
public class StoreSettlementService {

    private static final Logger logger = LoggerFactory.getLogger(StoreSettlementService.class);

    /** 商品沒有任何分類時的預設分類名稱 */
    private static final String UNCATEGORIZED = "未分類";

    // [B-6] 報表專用的訂單查詢（綁定 java.util.Date），取代 OrderJpaRepository 內綁 LocalDateTime 的舊查詢
    private final OrderReportQueryRepository orderReportQueryRepository;
    private final ProductCategoryReportQueryRepository productCategoryReportQueryRepository;
    // [命名修正] 欄位名稱原本與型別同名 (批次改名殘留)，改為 lowerCamelCase
    private final DailyStoreStatsJpaRepository dailyStoreStatsRepository;
    private final DailyProductStatsJpaRepository dailyProductStatsRepository;
    // [B-7] 營業時區：日結區間 (LocalDateTime) 要用它轉成 Date，而不是 JVM 預設時區
    private final ZoneId businessZoneId;

    public StoreSettlementService(OrderReportQueryRepository orderReportQueryRepository,
                                  ProductCategoryReportQueryRepository productCategoryReportQueryRepository,
                                  DailyStoreStatsJpaRepository dailyStoreStatsRepository,
                                  DailyProductStatsJpaRepository dailyProductStatsRepository,
                                  ZoneId businessZoneId) {
        this.orderReportQueryRepository = orderReportQueryRepository;
        this.productCategoryReportQueryRepository = productCategoryReportQueryRepository;
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.dailyProductStatsRepository = dailyProductStatsRepository;
        this.businessZoneId = businessZoneId;
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

        // [B-7] 以營業時區把日結區間轉成 java.util.Date（OrderEntity 的時間欄位型別）
        Date startDate = Date.from(start.atZone(businessZoneId).toInstant());
        Date endDate = Date.from(end.atZone(businessZoneId).toInstant());

        // --- 步驟 1: 準備分店統計物件 ---
        DailyStoreStatsEntity storeStats = new DailyStoreStatsEntity();
        storeStats.setStoreId(storeId);
        storeStats.setBrandId(brandId);
        storeStats.setDate(date);

        // --- 步驟 2: 查詢並統計訂單狀態（一次查詢涵蓋 CLOSED / CANCELLED / 未結案） ---
        // [B-6 修正] CLOSED 依 completedTime；CANCELLED 依 orderTime（取消單不會寫 completedTime，
        // 舊版用 completedTime 篩選導致取消數永遠是 0）
        List<OrderStatusStatsDto> statusStats = orderReportQueryRepository.findDailyStatusStats(storeId, startDate, endDate);

        // [效能] 未結案數整店只需要算一次（舊版在每個商品的迴圈內各查一次）
        long unclosedCount = 0;

        for (OrderStatusStatsDto dto : statusStats) {
            if (dto.getStatus() == OrderStatus.CLOSED) {
                storeStats.setTotalOrders(dto.getCount().intValue());
                // SUM 欄位可能為 null（例如折扣欄位未填），DB 欄位是 NOT NULL，所以統一轉成 0
                storeStats.setTotalRevenue(nz(dto.getTotalAmount()));
                storeStats.setTotalDiscount(nz(dto.getDiscountAmount()));
                storeStats.setFinalRevenue(nz(dto.getFinalAmount()));
            } else if (dto.getStatus() == OrderStatus.CANCELLED) {
                storeStats.setCancelledOrders(dto.getCount().intValue());
            } else if (OrderReportQueryRepository.UNCLOSED_STATUSES.contains(dto.getStatus())) {
                unclosedCount += dto.getCount();
            }
        }

        if (unclosedCount > 0) {
            logger.warn("【日結警告】分店 ID: {} 在日期 {} 尚有 {} 筆訂單未結案 (非 CLOSED/CANCELLED)。",
                    storeId, date, unclosedCount);
        }

        // --- 步驟 3: 查詢並統計付款方式 ---
        // [B-6 修正] 真實的付款代碼是 CASH / CREDIT_CARD / THIRD_PARTY（舊版只認 CASH 與不存在的 LINE_PAY）。
        // 查詢以 LEFT JOIN 依付款方式代碼分組；沒有付款方式 (代碼為 null) 或尚未列入細分的代碼，
        // 一律歸入「未指定」(unspecified_total)，所以四個欄位加總 = 實收金額。
        // line_pay_total 欄位保留不動 (維持 0)。對應規則與即時統計共用 PaymentBreakdown。
        List<PaymentStatsDto> paymentStats = orderReportQueryRepository.findClosedPaymentStats(storeId, startDate, endDate);

        PaymentBreakdown payments = PaymentBreakdown.empty();
        for (PaymentStatsDto dto : paymentStats) {
            if (dto.getTotalAmount() == null) {
                continue;
            }
            payments = payments.plus(PaymentBreakdown.of(dto.getPaymentCode(), dto.getTotalAmount()));
        }
        storeStats.setCashTotal(payments.cash());
        storeStats.setCreditCardTotal(payments.creditCard());
        storeStats.setThirdPartyTotal(payments.thirdParty());
        storeStats.setUnspecifiedTotal(payments.unspecified());

        dailyStoreStatsRepository.save(storeStats);

        // --- 步驟 4: 計算並儲存商品銷售統計 ---
        List<ProductSalesStatsDto> productStats = orderReportQueryRepository.findClosedProductStats(storeId, startDate, endDate);

        if (productStats.isEmpty()) {
            return;
        }

        // [效能] 分類名稱一次批次查詢（舊版在迴圈內對每個商品各查一次商品並展開 categories）
        Set<Long> productIds = productStats.stream()
                .map(ProductSalesStatsDto::getProductId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameByProduct = new HashMap<>();
        for (ProductCategoryReportQueryRepository.ProductCategoryRow row :
                productCategoryReportQueryRepository.findCategoryRows(brandId, productIds)) {
            // 查詢已依 (商品 ID, 分類名稱) 排序，同一商品只取第一個分類，結果穩定
            categoryNameByProduct.putIfAbsent(row.getProductId(), row.getCategoryName());
        }

        List<DailyProductStatsEntity> productStatsToSave = new ArrayList<>();
        for (ProductSalesStatsDto dto : productStats) {
            DailyProductStatsEntity prodStat = new DailyProductStatsEntity();
            prodStat.setStoreId(storeId);
            prodStat.setBrandId(brandId);
            prodStat.setProductId(dto.getProductId());
            prodStat.setProductName(dto.getProductName());
            prodStat.setDate(date);
            prodStat.setQuantitySold(dto.getTotalQuantity().intValue());
            prodStat.setTotalSalesAmount(dto.getTotalSalesAmount());
            prodStat.setCategoryName(categoryNameByProduct.getOrDefault(dto.getProductId(), UNCATEGORIZED));
            productStatsToSave.add(prodStat);
        }
        dailyProductStatsRepository.saveAll(productStatsToSave);
    }

    /** null 安全：金額為 null 時視為 0 */
    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
