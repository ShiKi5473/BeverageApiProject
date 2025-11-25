package tw.niels.beverage_api_project.modules.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.StoreRankingDto;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStats;
import tw.niels.beverage_api_project.modules.report.repository.DailyProductStatsRepository;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsRepository;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.REPORTS)
@Tag(name = "Report APIs", description = "報表與統計數據查詢介面")
public class ReportController {

    private final DailyStoreStatsRepository dailyStoreStatsRepository;
    private final DailyProductStatsRepository dailyProductStatsRepository; //
    private final ReportAggregationService reportAggregationService;
    private final ControllerHelperService helperService;
    private final StoreRepository storeRepository;

    public ReportController(DailyStoreStatsRepository dailyStoreStatsRepository,
                            DailyProductStatsRepository dailyProductStatsRepository,
                            ReportAggregationService reportAggregationService,
                            ControllerHelperService helperService,
                            StoreRepository storeRepository) {
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.dailyProductStatsRepository = dailyProductStatsRepository;
        this.reportAggregationService = reportAggregationService;
        this.helperService = helperService;
        this.storeRepository = storeRepository;
    }

    /**
     * 【分店端】查詢分店日結報表 (指定區間)
     * 用途：店長查看過去一週或一個月的每日營收明細
     * URL: GET /api/v1/reports/store-daily?storeId=1&startDate=2023-10-01&endDate=2023-10-31
     */
    @GetMapping("/store-daily")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
    @Operation(
            summary = "查詢分店日結明細",
            description = "取得指定分店在特定日期區間內的每日營收統計 (權限: 該分店員工或品牌管理員)"
    )
    public ResponseEntity<List<DailyStoreStats>> getStoreDailyStats(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // 驗證權限：確保該員工有權限查看此分店
        helperService.validateStoreAccess(storeId);

        List<DailyStoreStats> stats = dailyStoreStatsRepository
                .findByStoreIdAndDateBetweenOrderByDateAsc(storeId, startDate, endDate);

        return ResponseEntity.ok(stats);
    }

        /**
         * 【分店端】查詢熱銷商品排行 (指定區間)
         * 用途：查詢某段時間內，賣得最好的商品 (依照金額排序)
         * URL: GET /api/v1/reports/product-sales?storeId=1&startDate=...&endDate=...
         */
        @GetMapping("/product-sales")
        @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
        @Operation(
                summary = "查詢熱銷商品排行",
                description = "取得指定分店在區間內的商品銷售統計，依銷售總額排序 (用於繪製熱銷排行榜)"
        )
        public ResponseEntity<List<ProductSalesStatsDto>> getProductSalesRanking(
                @RequestParam Long storeId,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

            // 1. 驗證權限
            helperService.validateStoreAccess(storeId);

            // 2. 查詢資料庫
            List<ProductSalesStatsDto> stats = dailyProductStatsRepository
                    .findProductSalesRanking(storeId, startDate, endDate);

            return ResponseEntity.ok(stats);
        }

    /**
     * 【品牌端】查詢全品牌總覽 (指定區間)
     * 用途：老闆查看某個月全品牌的總業績 (所有分店加總)
     * URL: GET /api/v1/reports/brand-summary?startDate=2023-10-01&endDate=2023-10-31
     */
    @GetMapping("/brand-summary")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN')") // 僅品牌管理員可看
    @Operation(
            summary = "查詢全品牌銷售總覽",
            description = "取得全品牌在指定區間內的加總數據，包含總營收、總訂單數等 (權限: 品牌管理員)"
    )
    public ResponseEntity<BrandSalesSummaryDto> getBrandSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long brandId = helperService.getCurrentBrandId();

        BrandSalesSummaryDto summary = dailyStoreStatsRepository
                .aggregateBrandSales(brandId, startDate, endDate);

        return ResponseEntity.ok(summary);
    }

    /**
     * 【品牌端】查詢分店排行 (指定區間)
     * 用途：老闆查看哪些分店業績最好 (Top Stores)
     * URL: GET /api/v1/reports/store-ranking?startDate=2023-10-01&endDate=2023-10-31
     */
    @GetMapping("/store-ranking")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN')")
    @Operation(
            summary = "查詢分店營收排行",
            description = "取得各分店在指定區間內的實收金額排名 (權限: 品牌管理員)"
    )
    public ResponseEntity<List<StoreRankingDto>> getStoreRanking(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long brandId = helperService.getCurrentBrandId();

        List<StoreRankingDto> ranking = dailyStoreStatsRepository
                .findTopStoresByRevenue(brandId, startDate, endDate);

        Set<Long> storeIds = ranking.stream()
                .map(StoreRankingDto::getStoreId)
                .collect(Collectors.toSet());

        if (!storeIds.isEmpty()) {
            Map<Long, String> storeNameMap = storeRepository.findByBrand_IdAndIdIn(brandId, storeIds).stream()
                    .collect(Collectors.toMap(Store::getStoreId, Store::getName));

            ranking.forEach(dto -> {
                String name = storeNameMap.getOrDefault(dto.getStoreId(), "未知分店 (ID: " + dto.getStoreId() + ")");
                dto.setStoreName(name);
            });
        }

        return ResponseEntity.ok(ranking);
    }

    /**
     * 【測試/管理用】手動觸發某一天的結算
     * 用途：若排程失敗，或開發測試時手動補跑數據
     * URL: POST /api/v1/reports/trigger-aggregation?date=2023-10-20
     */
    @PostMapping("/trigger-aggregation")
    @PreAuthorize("hasRole('BRAND_ADMIN')") // 暫時開放給品牌管理員測試，正式環境建議只給 PLATFORM_ADMIN
    @Operation(
            summary = "手動觸發日結計算",
            description = "手動執行指定日期的報表結算，用於補漏或測試 (權限: 品牌管理員)"
    )
    public ResponseEntity<String> triggerAggregation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        reportAggregationService.generateDailyStats(date);

        return ResponseEntity.ok("已手動觸發 " + date + " 的報表結算。");
    }
}