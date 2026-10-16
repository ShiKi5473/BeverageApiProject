package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreIdentity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    private final StoreJpaRepository storeRepository;
    private final StoreSettlementService storeSettlementService;


    public ReportAggregationService(StoreJpaRepository storeRepository,
                                    StoreSettlementService storeSettlementService) {
        this.storeRepository = storeRepository;
        this.storeSettlementService = storeSettlementService;
    }

    /**
     * 執行指定日期的報表結算 (分頁批次處理)
     * <p>
     * 此方法本身 **不開啟** 交易，而是讓內部迴圈的每一筆處理獨立開啟交易。
     * 這樣可避免長交易 (Long Transaction) 鎖死資料庫資源，並實現「部分成功」的容錯機制。
     * </p>
     */
    public void generateDailyStats(LocalDate date) {
        logger.info("開始執行日結報表計算，目標日期: {}", date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        int successCount = 0;
        int failCount = 0;

        int pageNumber = 0;
        int pageSize = 50;
        Page<StoreIdentity> storePage;

        do {
            // 1. 查詢該頁分店
            storePage = storeRepository.findAllStoreIdentities(
                    PageRequest.of(pageNumber, pageSize, Sort.by("storeId"))
            );

            // 2. 處理該頁的分店
            for (StoreIdentity storeIdentity : storePage.getContent()) {
                try {
                    // 直接呼叫外部 Service，不需要 Self-Injection
                    storeSettlementService.processStoreStats(
                            storeIdentity.getStoreId(),
                            storeIdentity.getBrandId(),
                            date,
                            startOfDay, endOfDay);
                    successCount++;
                } catch (Exception e) {
                    logger.error("分店 ID: {} 日結失敗", storeIdentity.getStoreId(), e);
                    failCount++;
                }
            }

            pageNumber++;
            if (pageNumber % 10 == 0) {
                logger.info("已處理 {} 頁分店...", pageNumber);
            }

        } while (storePage.hasNext());

        logger.info("日結報表計算完成。成功: {}, 失敗: {}, 總分店數: {}", successCount, failCount, storePage.getTotalElements());
    }

    /**
     * [S-1 新增] 只結算「指定品牌」旗下分店的日結報表。
     * <p>
     * 手動觸發端點讓 BRAND_ADMIN 使用時，只能重算自己品牌的分店，
     * 不可像 {@link #generateDailyStats(LocalDate)} 那樣重算全平台所有品牌。
     * 同樣不開啟外層交易，每間分店由 StoreSettlementService 以獨立交易處理。
     * </p>
     */
    public void generateDailyStatsForBrand(Long brandId, LocalDate date) {
        logger.info("開始執行品牌日結報表計算，品牌 ID: {}，目標日期: {}", brandId, date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        int successCount = 0;
        int failCount = 0;

        // 單一品牌的分店數量有限，一次載入即可（只取 id，不展開關聯）
        for (StoreEntity store : storeRepository.findByBrand_Id(brandId)) {
            try {
                storeSettlementService.processStoreStats(store.getId(), brandId, date, startOfDay, endOfDay);
                successCount++;
            } catch (Exception e) {
                logger.error("品牌 {} 的分店 ID: {} 日結失敗", brandId, store.getId(), e);
                failCount++;
            }
        }

        logger.info("品牌 {} 日結報表計算完成。成功: {}, 失敗: {}", brandId, successCount, failCount);
    }
}