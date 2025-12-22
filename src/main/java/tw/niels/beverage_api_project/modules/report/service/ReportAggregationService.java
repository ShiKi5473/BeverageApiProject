package tw.niels.beverage_api_project.modules.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.store.repository.StoreIdentity;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

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

    private final StoreRepository storeRepository;
    private final StoreSettlementService storeSettlementService;


    public ReportAggregationService(StoreRepository storeRepository,
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
}