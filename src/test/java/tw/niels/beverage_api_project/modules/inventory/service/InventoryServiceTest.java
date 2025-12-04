package tw.niels.beverage_api_project.modules.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryItemRepository itemRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryBatchDAO inventoryBatchDAO;
    @Mock private ControllerHelperService helperService; // 即使沒直接用到，Service 依賴它也需要 Mock

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("庫存扣減 - 成功 (單一批次足夠)")
    void deductInventory_Success_SingleBatch() {
        // Arrange
        Long brandId = 1L;
        Long storeId = 1L;
        Long itemId = 100L;
        BigDecimal deductQty = new BigDecimal("5.0");

        InventoryItem mockItem = new InventoryItem();
        mockItem.setTotalQuantity(new BigDecimal("100.0")); // 總量足夠

        InventoryBatch mockBatch = new InventoryBatch();
        mockBatch.setBatchId(555L);
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
                        list.get(0).batchId().equals(555L) &&
                        list.get(0).newQuantity().compareTo(new BigDecimal("5.0")) == 0
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

        InventoryItem mockItem = new InventoryItem();
        mockItem.setTotalQuantity(new BigDecimal("50.0"));

        // 批次 A: 剩 10 (過期日較早)
        InventoryBatch batchA = new InventoryBatch();
        batchA.setBatchId(1L);
        batchA.setCurrentQuantity(new BigDecimal("10.0"));
        batchA.setExpiryDate(LocalDate.now().plusDays(1));

        // 批次 B: 剩 20 (過期日較晚)
        InventoryBatch batchB = new InventoryBatch();
        batchB.setBatchId(2L);
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

        InventoryItem mockItem = new InventoryItem();
        mockItem.setTotalQuantity(new BigDecimal("50.0")); // 只有 50

        when(itemRepository.findByBrandIdAndIdForUpdate(brandId, itemId)).thenReturn(Optional.of(mockItem));

        assertThrows(BadRequestException.class, () ->
                inventoryService.deductInventory(brandId, storeId, itemId, deductQty)
        );

        // 確保沒有執行後續 DB 更新
        verify(inventoryBatchDAO, never()).batchUpdateQuantities(any());
    }
}