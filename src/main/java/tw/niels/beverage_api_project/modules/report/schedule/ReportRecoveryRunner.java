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
import java.util.concurrent.Executors;

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

        LocalDate processingDate = startDate;

        // 使用 Java 21 虛擬線程 Executor
        // try-with-resources 會自動等待所有任務完成 (await termination) 才繼續往下執行
        // 這確保了在鎖釋放前，所有補跑作業都已完成。
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 限制同時只有 8 個任務在執行 (保護資料庫連線池)
            java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(8);

            // 【修正 2】 使用 !isAfter 確保「昨天」也會被執行
            while (!processingDate.isAfter(yesterday)) {
                final LocalDate dateToProcess = processingDate;

                try {
                    semaphore.acquire();

                    executor.submit(() -> {
                        try {
                            reportAggregationService.generateDailyStats(dateToProcess);
                        } finally {
                            semaphore.release(); // 任務結束釋放許可
                        }
                    });
                } catch (InterruptedException e) {
                    logger.error("補跑作業被中斷", e);
                    Thread.currentThread().interrupt(); // 恢復中斷狀態
                    break; // 中斷迴圈
                }

                processingDate = processingDate.plusDays(1);
            }

        } // 這裡會阻塞，直到所有提交給 executor 的任務都執行完畢

        logger.info("【報表補漏檢查】區間補跑作業已全部完成。");

    }
}