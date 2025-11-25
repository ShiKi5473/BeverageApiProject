package tw.niels.beverage_api_project;

import org.junit.jupiter.api.Test;

class BeverageApiProjectApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // 這個測試現在會啟動真實的 Docker 容器 (Postgres, Redis, RabbitMQ)
        // 來驗證 Spring Context 是否能正確載入並連線
    }

}