package tw.niels.beverage_api_project.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InventoryConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Test
    @DisplayName("併發庫存扣減測試 - 10 個執行緒同時搶購")
    void testConcurrentInventoryDeduction() throws InterruptedException {
        // Arrange:
        // 假設 DataSeeder 已經初始化了 item ID = 1 (總量通常在 DataSeeder 設定，這裡我們假設原本充足或我們手動重設)
        // 為了測試準確，我們手動設定一個已知量
        Long brandId = 1L;
        Long storeId = 1L;
        Long itemId = 1L; // 假設是紅茶

        // 先手動將庫存設為 100
        InventoryItem item = itemRepository.findByBrand_IdAndId(brandId, itemId).orElseThrow();
        item.setTotalQuantity(new BigDecimal("100.00"));
        itemRepository.save(item);

        int numberOfThreads = 10;
        BigDecimal qtyPerThread = new BigDecimal("5.00"); // 每個執行緒扣 5

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.deductInventory(brandId, storeId, itemId, qtyPerThread);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("扣減失敗: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有執行緒完成

        // Assert
        InventoryItem updatedItem = itemRepository.findByBrand_IdAndId(brandId, itemId).orElseThrow();

        // 1. 確保所有交易都成功 (因為總量 100 > 10 * 5)
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(numberOfThreads);

        // 2. 驗證最終庫存 (100 - 50 = 50)
        // 注意：這裡只驗證了 Item 表的預扣 (TotalQuantity)，
        // 若要驗證 Batch 表，需額外 Inject BatchRepository 檢查
        assertThat(updatedItem.getTotalQuantity()).isEqualByComparingTo("50.00");
    }
}