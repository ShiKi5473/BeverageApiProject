package tw.niels.beverage_api_project.modules.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;
// 修���：Entity 類別必��加上 Entity 後綴，Repository 必須加上 Jpa 前綴
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryItemJpaRepository itemRepository;
    @Mock private InventoryBatchJpaRepository batchRepository;
    @Mock private InventoryBatchDAO inventoryBatchDAO;
    @Mock private StoreJpaRepository storeRepository; // InventoryDeductionService 依賴

    // 注意：測試對象已改為 InventoryDeductionService（包含 4 參數的 deductInventory 方法）
    @InjectMocks
    private InventoryDeductionService inventoryService;

    @Test
    @DisplayName("庫存扣減 - 成功 (單一批次足夠)")
    void deductInventory_Success_SingleBatch() {
        // Arrange
        Long brandId = 1L;
        Long storeId = 1L;
        Long itemId = 100L;
        BigDecimal deductQty = new BigDecimal("5.0");

        InventoryItemEntity mockItem = new InventoryItemEntity();
        mockItem.setTotalQuantity(new BigDecimal("100.0")); // 總量足夠

        InventoryBatchEntity mockBatch = new InventoryBatchEntity();
        mockBatch.setId(555L);
        mockBatch.setCurrentQuantity(new BigDecimal("10.0")); // 批次足夠
        mockBatch.setExpiryDate(LocalDate.now().plusDays(10));

        when(itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)).thenReturn(Optional.of(mockItem));
        when(batchRepository.findAvailableBatchesForUpdate(storeId, itemId)).thenReturn(Collections.singletonList(mockBatch));

        // Act
        inventoryService.deductInventory(brandId, storeId, itemId, deductQty);

        // Assert
        // 1. 驗證總庫存有更新 (100 - 5 = 95)
        assertThat(mockItem.getTotalQuantity()).isEqualByComparingTo("95.0");
        verify(itemRepository).save(mockItem);

        // 2. 驗證 DAO 被呼叫，且更新值正確 (10 - 5 = 5)
        verify(inventoryBatchDAO).batchUpdateQuantities(argThat(list ->
                list.size() == 1 &&
                        list.getFirst().batchId().equals(555L) &&
                        list.getFirst().newQuantity().compareTo(new BigDecimal("5.0")) == 0
        ));
    }

    @Test
    @DisplayName("庫存扣減 - 成功 (跨批次 FIFO)")
    void deductInventory_Success_MultiBatch() {
        // Arrange
        Long brandId = 1L;
        Long storeId = 1L;
        Long itemId = 100L;
        BigDecimal deductQty = new BigDecimal("15.0"); // 要扣 15

        InventoryItemEntity mockItem = new InventoryItemEntity();
        mockItem.setTotalQuantity(new BigDecimal("50.0"));

        // 批次 A: 剩 10 (過期日較早)
        InventoryBatchEntity batchA = new InventoryBatchEntity();
        batchA.setId(1L);
        batchA.setCurrentQuantity(new BigDecimal("10.0"));
        batchA.setExpiryDate(LocalDate.now().plusDays(1));

        // 批次 B: 剩 20 (過期日較晚)
        InventoryBatchEntity batchB = new InventoryBatchEntity();
        batchB.setId(2L);
        batchB.setCurrentQuantity(new BigDecimal("20.0"));
        batchB.setExpiryDate(LocalDate.now().plusDays(5));

        when(itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)).thenReturn(Optional.of(mockItem));
        // 模擬 Repository 依效期排序回傳
        when(batchRepository.findAvailableBatchesForUpdate(storeId, itemId)).thenReturn(Arrays.asList(batchA, batchB));

        // Act
        inventoryService.deductInventory(brandId, storeId, itemId, deductQty);

        // Assert
        // 驗證 DAO 呼叫參數
        verify(inventoryBatchDAO).batchUpdateQuantities(argThat(list -> {
            if (list.size() != 2) return false;
            // 批次 A 應該被扣光 (變 0)
            boolean batchAOk = list.stream().anyMatch(t -> t.batchId().equals(1L) && t.newQuantity().compareTo(BigDecimal.ZERO) == 0);
            // 批次 B 應該扣掉剩餘的 5 (20 - 5 = 15)
            boolean batchBOk = list.stream().anyMatch(t -> t.batchId().equals(2L) && t.newQuantity().compareTo(new BigDecimal("15.0")) == 0);
            return batchAOk && batchBOk;
        }));
    }

    @Test
    @DisplayName("庫存扣減 - 失敗 (總量不足)")
    void deductInventory_Fail_InsufficientTotal() {
        Long brandId = 1L;
        Long storeId = 1L;
        Long itemId = 100L;
        BigDecimal deductQty = new BigDecimal("100.0");

        InventoryItemEntity mockItem = new InventoryItemEntity();
        mockItem.setTotalQuantity(new BigDecimal("50.0")); // 只有 50

        when(itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)).thenReturn(Optional.of(mockItem));

        assertThrows(BadRequestException.class, () ->
                inventoryService.deductInventory(brandId, storeId, itemId, deductQty)
        );

        // 確保沒有執行後續 DB 更新
        verify(inventoryBatchDAO, never()).batchUpdateQuantities(any());
    }
}
