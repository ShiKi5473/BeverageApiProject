package tw.niels.beverage_api_project.modules.report.schedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock; // 新增 import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;

import java.time.LocalDate;

@Component
public class ReportScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReportScheduler.class);
    private final ReportAggregationService reportAggregationService;

    public ReportScheduler(ReportAggregationService reportAggregationService) {
        this.reportAggregationService = reportAggregationService;
    }

    /**
     * 每天凌晨 03:00 執行
     * 加入 @SchedulerLock 確保叢集環境下單一執行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(
            name = "DailyReportAggregation",
            lockAtMostFor = "30m",    // 鎖最多持有 30 分鐘 (如果執行超過這時間，鎖會強制釋放)
            lockAtLeastFor = "1m"     // 鎖最少持有 1 分鐘 (防止時鐘不同步導致重複執行)
    )
    public void runDailyReportAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        logger.info("【排程啟動】開始執行日結報表計算 (含分散式鎖)，目標日期: {}", yesterday);

        try {
            long startTime = System.currentTimeMillis();
            reportAggregationService.generateDailyStats(yesterday);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("【排程結束】日結報表計算完成，耗時: {} ms", duration);
        } catch (Exception e) {
            logger.error("【排程錯誤】執行日結報表時發生未預期的錯誤", e);
        }
    }
}