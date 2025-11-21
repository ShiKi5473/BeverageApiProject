package tw.niels.beverage_api_project.modules.report.schedule;

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
     * 結算「昨天」的數據
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void runDailyReportAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        logger.info("排程啟動：開始結算 {} 的報表...", yesterday);

        try {
            reportAggregationService.generateDailyStats(yesterday);
        } catch (Exception e) {
            logger.error("排程執行失敗：報表結算發生錯誤", e);
        }
    }
}