package tw.niels.beverage_api_project.modules.report.schedule;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStats;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsRepository;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class ReportRecoveryRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReportRecoveryRunner.class);

    private final ReportAggregationService reportAggregationService;
    private final DailyStoreStatsRepository dailyStoreStatsRepository;
    private final LockProvider lockProvider; // 注入 LockProvider

    public ReportRecoveryRunner(ReportAggregationService reportAggregationService,
                                DailyStoreStatsRepository dailyStoreStatsRepository,
                                LockProvider lockProvider) {
        this.reportAggregationService = reportAggregationService;
        this.dailyStoreStatsRepository = dailyStoreStatsRepository;
        this.lockProvider = lockProvider;
    }

    @Override
    public void run(ApplicationArguments args) throws RuntimeException {
        // 設定鎖的組態
        // name: 鎖的名稱 (必須唯一)
        // lockAtMostFor: 鎖最多持有 10 分鐘 (預防程式當掉鎖死)
        // lockAtLeastFor: 鎖最少持有 10 秒
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "ReportStartupRecovery",
                Duration.ofMinutes(10),
                Duration.ofSeconds(10)
        );

        // 嘗試獲取鎖
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);

        if (lock.isPresent()) {
            try {
                logger.info("【報表補漏檢查】已獲取分散式鎖，開始檢查...");
                performRecoveryLogic();
            } finally {
                // 釋放鎖
                lock.get().unlock();
                logger.info("【報表補漏檢查】作業結束，鎖已釋放。");
            }
        } else {
            logger.info("【報表補漏檢查】其他實例正在執行補漏作業，本實例跳過。");
        }
    }

    private void performRecoveryLogic() {
        // 1. 目標：結算到「昨天」
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 2. 查詢資料庫
        Optional<DailyStoreStats> lastStatOpt = dailyStoreStatsRepository.findTopByOrderByDateDesc();
        LocalDate startDate;

        if (lastStatOpt.isPresent()) {
            startDate = lastStatOpt.get().getDate().plusDays(1);
        } else {
            logger.info("【報表補漏檢查】查無歷史報表，將嘗試執行昨天的結算。");
            startDate = yesterday;
        }

        if (startDate.isAfter(yesterday)) {
            logger.info("【報表補漏檢查】資料已是最新，無需補跑。");
            return;
        }

        logger.info("【報表補漏檢查】發現數據缺漏！區間: {} 到 {}", startDate, yesterday);

        // 非同步執行建議：
        // 如果補跑天數很多，這裡可以再開一個 new Thread 或是把 process 丟給 @Async
        // 但因為我們已經搶到鎖了，在鎖的有效期間內跑完是最安全的。

        LocalDate processingDate = startDate;
        while (!processingDate.isAfter(yesterday)) {
            try {
                logger.info(">> [Recovery] 正在補跑 {} 的報表...", processingDate);
                reportAggregationService.generateDailyStats(processingDate);
            }catch (RuntimeException e){
                logger.error(">> [Recovery] 補跑 {} 失敗", processingDate, e);
            }
            processingDate = processingDate.plusDays(1);
        }
    }
}