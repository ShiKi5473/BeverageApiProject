package tw.niels.beverage_api_project.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tw.niels.beverage_api_project.AbstractIntegrationTest;
// 修正：Entity 類別必須加上 Entity 後綴，Repository 必須加上 Jpa 前綴
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryDeductionService;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class InventoryConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryDeductionService inventoryService;
    @Autowired
    private InventoryItemJpaRepository itemRepository;
    @Autowired
    private InventoryBatchJpaRepository batchRepository;
    @Autowired
    private StoreJpaRepository storeRepository;
    @Autowired
    private BrandJpaRepository brandRepository;

    @Test
    @DisplayName("併發庫存扣減測試 - 10 個執行緒同時搶購 (FIFO 驗證)")
    void testConcurrentInventoryDeduction() throws InterruptedException {
        // --- Arrange ---
        // 1. 準備基礎資料
        BrandEntity brand = new BrandEntity();
        brand.setName("Test Brand");
        brand = brandRepository.save(brand);

        StoreEntity store = new StoreEntity();
        store.setName("Test Store");
        store.setBrand(brand);
        store = storeRepository.save(store);

        InventoryItemEntity item = new InventoryItemEntity();
        item.setBrand(brand);
        item.setName("Concurrency Tea");
        item.setUnit("ml");
        item.setTotalQuantity(new BigDecimal("100.00"));
        item = itemRepository.save(item);

        Long storeId = store.getId();
        Long itemId = item.getId();
        Long brandId = brand.getId(); // Service 可能需要

        // 2. 準備批次 (模擬 FIFO)
        // Batch A: 30 (較早過期)
        createTestBatch(store, item, new BigDecimal("30.00"), LocalDate.now().plusDays(10));
        // Batch B: 70 (較晚過期)
        createTestBatch(store, item, new BigDecimal("70.00"), LocalDate.now().plusDays(20));

        // 3. 併發設定
        int numberOfThreads = 10;
        BigDecimal qtyPerThread = new BigDecimal("5.00"); // 總共扣 50

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger failCount = new AtomicInteger(0);

        // --- Act ---
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    // 請確認這裡的方法簽章是否匹配您的 Service (brandId/storeId)
                    inventoryService.deductInventory(brandId, storeId, itemId, qtyPerThread);
                } catch (Exception e) {
                    System.out.println("扣減失敗: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // --- Assert ---
        // 1. 確保全部成功
        assertThat(failCount.get()).as("所有並發請求都應成功").isEqualTo(0);

        // 2. 驗證 Item 總庫存 (100 - 50 = 50)
        InventoryItemEntity updatedItem = itemRepository.findByBrand_IdAndId(brandId, itemId).orElseThrow();
        assertThat(updatedItem.getTotalQuantity()).isEqualByComparingTo("50.00");

        // 3. 驗證 Batch FIFO (使用我們剛新增的 findByStore_Id)
        List<InventoryBatchEntity> batches = batchRepository.findByStore_Id(storeId).stream()
                .filter(b -> b.getInventoryItem().getId().equals(itemId))
                .sorted(Comparator.comparing(InventoryBatchEntity::getExpiryDate))
                .toList();

        assertThat(batches).hasSize(2);

        // 第一批 (30) 應該被扣光 -> 剩 0
        assertThat(batches.get(0).getCurrentQuantity()).isEqualByComparingTo("0.00");

        // 第二批 (70) 應該被扣 20 -> 剩 50
        assertThat(batches.get(1).getCurrentQuantity()).isEqualByComparingTo("50.00");
    }

    private void createTestBatch(StoreEntity store, InventoryItemEntity item, BigDecimal qty, LocalDate expiryDate) {
        InventoryBatchEntity batch = new InventoryBatchEntity();
        batch.setStore(store);
        batch.setInventoryItem(item);
        batch.setQuantityReceived(qty);
        batch.setCurrentQuantity(qty);
        batch.setExpiryDate(expiryDate);
        batch.setProductionDate(LocalDate.now());
        batch.setShipment(null); // 重要：測試無進貨單的情況 (需配合 DB schema 允許 null)
        batchRepository.save(batch);
    }
}
