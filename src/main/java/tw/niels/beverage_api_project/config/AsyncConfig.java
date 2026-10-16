package tw.niels.beverage_api_project.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 非同步執行緒池設定。
 * <p>
 * 【修改】
 * <ul>
 *   <li>拆分 KDS 推播（kdsExecutor）與稽核日誌（auditExecutor），避免稽核寫入 MongoDB 變慢時拖累廚房即時推播</li>
 *   <li>原本佇列 100 + 預設 AbortPolicy，滿載時事件直接被丟棄；改為 CallerRunsPolicy（由發布者執行緒自行執行，形成背壓而不丟事件）</li>
 *   <li>新增 MDC TaskDecorator，把 traceId 等 MDC 內容帶到非同步執行緒，log 可串連同一請求</li>
 *   <li>保留名為 taskExecutor 的 @Primary 預設執行緒池（未指定名稱的 @Async 使用）</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** KDS 推播與顧客通知使用的執行緒池名稱（@Async("kdsExecutor")） */
    public static final String KDS_EXECUTOR = "kdsExecutor";
    /** 稽核日誌使用的執行緒池名稱（@Async("auditExecutor")） */
    public static final String AUDIT_EXECUTOR = "auditExecutor";

    @Bean(name = "taskExecutor")
    @Primary // 當有多個 Executor 時，預設使用這個
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心執行緒數：預設維持的執行緒數量
        executor.setCorePoolSize(5);
        // 最大執行緒數：當佇列滿時，最多可擴展到的數量
        executor.setMaxPoolSize(20);
        // 佇列容量：緩衝等待執行的任務數量
        executor.setQueueCapacity(100);
        // 執行緒名稱前綴，方便在 Log 中識別 (e.g., Async-1, Async-2)
        executor.setThreadNamePrefix("Async-");
        // 【新增】滿載時由呼叫者執行，避免 AbortPolicy 丟棄事件（如累點、報表）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcTaskDecorator());
        // 關機時等待進行中的任務完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 【新增】KDS／顧客通知專用執行緒池。
     * 監聽器在 AFTER_COMMIT 階段執行，CallerRunsPolicy 確保高峰時推播不會被丟棄。
     */
    @Bean(name = KDS_EXECUTOR)
    public Executor kdsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Kds-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 【新增】稽核日誌專用執行緒池（寫入 MongoDB）。
     * 稽核寫入較不即時，佇列較大；滿載時同樣由呼叫者執行，避免遺失稽核紀錄。
     */
    @Bean(name = AUDIT_EXECUTOR)
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("Audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 【新增】把提交任務當下的 MDC（含 traceId / spanId）複製到執行緒池的工作執行緒，執行完畢後還原，
     * 避免執行緒重用時殘留上一個任務的 MDC。
     */
    private static TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    } else {
                        MDC.clear();
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
