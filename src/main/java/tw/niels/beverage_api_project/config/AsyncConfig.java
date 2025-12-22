package tw.niels.beverage_api_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

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
        executor.initialize();
        return executor;
    }
}