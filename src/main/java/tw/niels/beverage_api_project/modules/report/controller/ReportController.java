package tw.niels.beverage_api_project.modules.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.report.dto.BrandSalesSummaryDto;
import tw.niels.beverage_api_project.modules.report.dto.DailyStoreStatsResponseDto;
import tw.niels.beverage_api_project.modules.report.dto.InventoryVarianceReportDto;
import tw.niels.beverage_api_project.modules.report.dto.ProductSalesStatsDto;
import tw.niels.beverage_api_project.modules.report.dto.StoreRankingDto;
import tw.niels.beverage_api_project.modules.report.repository.DailyProductStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.service.InventoryReportService;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.REPORTS)
@Tag(name = "Report APIs", description = "報表與統計數據查詢介面")
public class ReportController {

    // [命名修正] 欄位名稱原本與型別同名 (批次改名殘留)，改為 lowerCamelCase
    private final DailyStoreStatsJpaRepository dailyStoreStatsRepository;
    private final DailyProductStatsJpaRepository dailyProductStatsRepository;
    private final ReportAggregationService reportAggregationService;
    private final ControllerHelperService helperService;
    private final StoreJpaRepository storeRepository;
    private final InventoryReportService inventoryReportService;

    public ReportController(DailyStoreStatsJpaRepository dailyStoreStatsRepository,
                            DailyProductStatsJpaRepository dailyProductStatsRepository,
                            ReportAggregationService reportAggregationService,
                            ControllerHelperService helperService,
                            StoreJpaRepository storeRepository,
                            InventoryReportService inventoryReportService) {
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.dailyProductStatsRepository = dailyProductStatsRepository;
        this.reportAggregationService = reportAggregationService;
        this.helperService = helperService;
        this.storeRepository = storeRepository;
        this.inventoryReportService = inventoryReportService;
    }

    /**
     * 【分店端】查詢分店日結報表 (指定區間)
     * 用途：店長查看過去一週或一個月的每日營收明細
     * URL: GET /api/v1/reports/store-daily?storeId=1&startDate=2023-10-01&endDate=2023-10-31
     */
    @GetMapping("/store-daily")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    @Operation(
            summary = "查詢分店日結明細",
            description = "取得指定分店在特定日期區間內的每日營收統計 (權限: 該分店員工或品牌管理員)"
    )
    public ResponseEntity<List<DailyStoreStatsResponseDto>> getStoreDailyStats(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // 驗證權限：確保該員工有權限查看此分店
        helperService.validateStoreAccess(storeId);

        // [P0-10] 回傳 DTO（ID 為字串），不再直接把 JPA Entity 序列化給前端
        List<DailyStoreStatsResponseDto> stats = dailyStoreStatsRepository
                .findByStoreIdAndDateBetweenOrderByDateAsc(storeId, startDate, endDate)
                .stream()
                .map(DailyStoreStatsResponseDto::from)
                .toList();

        return ResponseEntity.ok(stats);
    }

    /**
     * 【分店端】查詢熱銷商品排行 (指定區間)
     * 用途：查詢某段時間內，賣得最好的商品 (依照金額排序)
     * URL: GET /api/v1/reports/product-sales?storeId=1&startDate=...&endDate=...
     */
    @GetMapping("/product-sales")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
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
                    .collect(Collectors.toMap(StoreEntity::getId, StoreEntity::getName));

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
    // [S-1 修正] 原本任何 BRAND_ADMIN 都能重算「全平台所有品牌」的分店。
    // 現在：PLATFORM_ADMIN 重算全部；BRAND_ADMIN 只重算自己品牌的分店。
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BRAND_ADMIN')")
    @Operation(
            summary = "手動觸發日結計算",
            description = "手動執行指定日期的報表結算，用於補漏或測試 (權限: 平台管理員可結算全部品牌；品牌管理員只能結算自己品牌的分店)"
    )
    public ResponseEntity<String> triggerAggregation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {

        boolean isPlatformAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> AuthorityConstants.ROLE_PLATFORM_ADMIN.equals(a.getAuthority()));

        if (isPlatformAdmin) {
            // 平台管理員：全部品牌
            reportAggregationService.generateDailyStats(date);
            return ResponseEntity.ok("已手動觸發 " + date + " 全部品牌的報表結算。");
        }

        // 品牌管理員：只結算自己品牌的分店
        Long brandId = helperService.getCurrentBrandId();
        reportAggregationService.generateDailyStatsForBrand(brandId, date);
        return ResponseEntity.ok("已手動觸發 " + date + " 的報表結算（僅限所屬品牌）。");
    }

    /**
     * 【分店端】查詢庫存耗損分析報表
     */
    @GetMapping("/inventory-variance")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')") // 僅管理員與店長可看
    @Operation(summary = "庫存耗損分析報表", description = "計算指定區間內的 理論耗用 vs 實際耗用 差異")
    public ResponseEntity<List<InventoryVarianceReportDto>> getInventoryVariance(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        helperService.validateStoreAccess(storeId);
        Long brandId = helperService.getCurrentBrandId();

        List<InventoryVarianceReportDto> report = inventoryReportService.generateVarianceReport(brandId, storeId, startDate, endDate);

        return ResponseEntity.ok(report);
    }
}
