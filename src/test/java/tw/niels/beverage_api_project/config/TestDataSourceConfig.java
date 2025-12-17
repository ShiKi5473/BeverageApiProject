package tw.niels.beverage_api_project.config;

import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@TestConfiguration
public class TestDataSourceConfig {

    @Bean
    public BeanPostProcessor dataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource) {
                    // 避免重複包裝 (如果已經被其他 proxy 包過)
                    if (bean.getClass().getName().contains("ProxyDataSource")) {
                        return bean;
                    }

                    ChainListener listener = new ChainListener();
                    // 核心：綁定 Hypersistence 的計數器
                    listener.addListener(new DataSourceQueryCountListener());
                    // 選用：印出 SQL 幫助除錯
                    listener.addListener(new SLF4JQueryLoggingListener());

                    return ProxyDataSourceBuilder
                            .create((DataSource) bean)
                            .name("DataSource-Proxy")
                            .listener(listener)
                            .build();
                }
                return bean;
            }
        };
    }
}