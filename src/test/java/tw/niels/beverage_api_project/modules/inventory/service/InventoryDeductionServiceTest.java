package tw.niels.beverage_api_project.modules.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshotEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryDeductionService 單元測試 (原 InventoryServiceTest，檔名與被測類別對齊後更名)。
 *
 * <p>[B-5] 涵蓋的修正：</p>
 * <ul>
 *   <li>扣減前的庫存檢查改用「本店批次總和」(sumQuantityByStoreAndItem)，而不是品牌層級 totalQuantity。</li>
 *   <li>扣減後同步減少分店 InventorySnapshot，避免下次盤點把已售數量當成盤損再扣一次。</li>
 *   <li>批次扣減 (processBatchDeductions) 同樣以批次總和檢查，並同步快照。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InventoryDeductionServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 1L;
    private static final Long ITEM_ID = 100L;

    @Mock private InventoryItemJpaRepository itemRepository;
    @Mock private InventoryBatchJpaRepository batchRepository;
    @Mock private InventoryBatchDAO inventoryBatchDAO;
    @Mock private StoreJpaRepository storeRepository; // InventoryDeductionService 依賴
    // [B-5 新增] 扣減後要同步分店庫存快照
    @Mock private InventorySnapshotJpaRepository snapshotRepository;

    @InjectMocks
    private InventoryDeductionService inventoryService;

    // ───────────── 測試資料輔助方法 ─────────────

    private InventoryItemEntity item(Long id, String total) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(id);
        item.setName("Item-" + id);
        item.setTotalQuantity(new BigDecimal(total));
        return item;
    }

    private InventoryBatchEntity batch(Long id, InventoryItemEntity item, String qty, int expiryInDays) {
        InventoryBatchEntity batch = new InventoryBatchEntity();
        batch.setId(id);
        batch.setInventoryItem(item);
        batch.setCurrentQuantity(new BigDecimal(qty));
        batch.setExpiryDate(LocalDate.now().plusDays(expiryInDays));
        return batch;
    }

    private InventorySnapshotEntity snapshot(InventoryItemEntity item, String qty) {
        InventorySnapshotEntity snapshot = new InventorySnapshotEntity();
        snapshot.setInventoryItem(item);
        snapshot.setQuantity(new BigDecimal(qty));
        return snapshot;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<InventorySnapshotEntity> capturedSavedSnapshots() {
        ArgumentCaptor<Iterable<InventorySnapshotEntity>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(snapshotRepository).saveAll(captor.capture());
        return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
    }

    // ───────────── 單品項扣減 ─────────────

    @Test
    @DisplayName("庫存扣減 - 成功 (單一批次足夠)，並同步減少快照")
    void deductInventory_Success_SingleBatch() {
        // Arrange
        BigDecimal deductQty = new BigDecimal("5.0");

        InventoryItemEntity mockItem = item(ITEM_ID, "100.0"); // 品牌總量足夠
        InventoryBatchEntity mockBatch = batch(555L, mockItem, "10.0", 10); // 批次足夠
        InventorySnapshotEntity snapshot = snapshot(mockItem, "10.0");

        when(itemRepository.findByBrandIdAndIdForUpdate(BRAND_ID, ITEM_ID)).thenReturn(Optional.of(mockItem));
        // [B-5] 快速檢查使用本店批次總和
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_ID)).thenReturn(Optional.of(new BigDecimal("10.0")));
        when(batchRepository.findAvailableBatchesForUpdate(STORE_ID, ITEM_ID)).thenReturn(Collections.singletonList(mockBatch));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(snapshot));

        // Act
        inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, deductQty);

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

        // 3. [B-5] 快照 = 扣減後的批次總和 (10 - 5 = 5)，並且有被儲存
        assertThat(snapshot.getQuantity()).isEqualByComparingTo("5.0");
        assertThat(capturedSavedSnapshots()).containsExactly(snapshot);
    }

    @Test
    @DisplayName("庫存扣減 - 成功 (跨批次 FIFO)")
    void deductInventory_Success_MultiBatch() {
        // Arrange
        BigDecimal deductQty = new BigDecimal("15.0"); // 要扣 15

        InventoryItemEntity mockItem = item(ITEM_ID, "50.0");
        // 批次 A: 剩 10 (過期日較早)
        InventoryBatchEntity batchA = batch(1L, mockItem, "10.0", 1);
        // 批次 B: 剩 20 (過期日較晚)
        InventoryBatchEntity batchB = batch(2L, mockItem, "20.0", 5);
        InventorySnapshotEntity snapshot = snapshot(mockItem, "30.0");

        when(itemRepository.findByBrandIdAndIdForUpdate(BRAND_ID, ITEM_ID)).thenReturn(Optional.of(mockItem));
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_ID)).thenReturn(Optional.of(new BigDecimal("30.0")));
        // 模擬 Repository 依效期排序回傳
        when(batchRepository.findAvailableBatchesForUpdate(STORE_ID, ITEM_ID)).thenReturn(Arrays.asList(batchA, batchB));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(snapshot));

        // Act
        inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, deductQty);

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

        // 快照 = 30 - 15 = 15
        assertThat(snapshot.getQuantity()).isEqualByComparingTo("15.0");
    }

    @Test
    @DisplayName("庫存扣減 - 失敗 (本店批次總和不足，即使品牌總量足夠)")
    void deductInventory_Fail_InsufficientStoreStock_EvenIfBrandTotalIsEnough() {
        BigDecimal deductQty = new BigDecimal("100.0");

        // 品牌層級總量 1000 (其他分店有庫存)，但本店批次總和只有 50
        InventoryItemEntity mockItem = item(ITEM_ID, "1000.0");

        when(itemRepository.findByBrandIdAndIdForUpdate(BRAND_ID, ITEM_ID)).thenReturn(Optional.of(mockItem));
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_ID)).thenReturn(Optional.of(new BigDecimal("50.0")));

        assertThrows(BadRequestException.class, () ->
                inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, deductQty)
        );

        // 確保沒有執行後續 DB 更新，品牌總量也沒有被動到
        verify(inventoryBatchDAO, never()).batchUpdateQuantities(any());
        verify(snapshotRepository, never()).saveAll(any());
        assertThat(mockItem.getTotalQuantity()).isEqualByComparingTo("1000.0");
    }

    @Test
    @DisplayName("庫存扣減 - 失敗 (品牌總量偏小但本店批次足夠時仍應成功，不再誤判不足)")
    void deductInventory_Success_WhenBrandTotalIsStaleButStoreBatchesAreEnough() {
        // 舊版進貨不累加 totalQuantity，品牌總量可能偏小 (這裡 0)；本店批次其實有 20
        InventoryItemEntity mockItem = item(ITEM_ID, "0.0");
        InventoryBatchEntity mockBatch = batch(9L, mockItem, "20.0", 3);

        when(itemRepository.findByBrandIdAndIdForUpdate(BRAND_ID, ITEM_ID)).thenReturn(Optional.of(mockItem));
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_ID)).thenReturn(Optional.of(new BigDecimal("20.0")));
        when(batchRepository.findAvailableBatchesForUpdate(STORE_ID, ITEM_ID)).thenReturn(List.of(mockBatch));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), anyCollection())).thenReturn(List.of());

        inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, new BigDecimal("5.0"));

        // 品牌總量下限為 0 (避免違反 CHECK 約束)
        assertThat(mockItem.getTotalQuantity()).isEqualByComparingTo("0.0");
        verify(inventoryBatchDAO).batchUpdateQuantities(argThat(list ->
                list.size() == 1 && list.getFirst().newQuantity().compareTo(new BigDecimal("15.0")) == 0));
    }

    @Test
    @DisplayName("庫存扣減 - 失敗 (本店完全沒有批次)")
    void deductInventory_Fail_NoBatches() {
        InventoryItemEntity mockItem = item(ITEM_ID, "50.0");

        when(itemRepository.findByBrandIdAndIdForUpdate(BRAND_ID, ITEM_ID)).thenReturn(Optional.of(mockItem));
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, new BigDecimal("1.0")));
        verify(inventoryBatchDAO, never()).batchUpdateQuantities(any());
    }

    @Test
    @DisplayName("庫存扣減 - 扣減數量必須大於 0")
    void deductInventory_Fail_NonPositiveQuantity() {
        assertThrows(BadRequestException.class, () ->
                inventoryService.deductInventory(BRAND_ID, STORE_ID, ITEM_ID, BigDecimal.ZERO));
        verify(itemRepository, never()).findByBrandIdAndIdForUpdate(any(), any());
    }

    // ───────────── 批次扣減 (訂單完成 / 盤損) ─────────────

    @Test
    @DisplayName("批次扣減 - 成功：依批次總和檢查、扣品牌總量、同步快照 (快照不存在則建立)")
    void processBatchDeductions_Success_SyncsSnapshots() {
        InventoryItemEntity item1 = item(1L, "100.0");
        InventoryItemEntity item2 = item(2L, "50.0");

        // item1：兩個批次共 50；item2：一個批次 8
        InventoryBatchEntity b1 = batch(11L, item1, "30.0", 1);
        InventoryBatchEntity b2 = batch(12L, item1, "20.0", 5);
        InventoryBatchEntity b3 = batch(13L, item2, "8.0", 2);
        // 只有 item1 已有快照；item2 沒有，應建立新快照
        InventorySnapshotEntity snap1 = snapshot(item1, "50.0");

        // 故意用無序的 HashMap：實作內部必須自行依 itemId 排序處理
        Map<Long, BigDecimal> deductionMap = new HashMap<>();
        deductionMap.put(2L, new BigDecimal("5.0"));
        deductionMap.put(1L, new BigDecimal("10.0"));

        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), anySet())).thenReturn(List.of(item1, item2));
        when(batchRepository.findAvailableBatchesForItemsForUpdate(eq(STORE_ID), anySet())).thenReturn(List.of(b1, b2, b3));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), anyCollection())).thenReturn(List.of(snap1));

        inventoryService.processBatchDeductions(BRAND_ID, STORE_ID, deductionMap);

        // 品牌總量
        assertThat(item1.getTotalQuantity()).isEqualByComparingTo("90.0");
        assertThat(item2.getTotalQuantity()).isEqualByComparingTo("45.0");

        // 批次扣減：item1 扣 10 全從最早到期的 b1 (30 -> 20)；item2 扣 5 從 b3 (8 -> 3)
        verify(inventoryBatchDAO).batchUpdateQuantities(argThat(list ->
                list.size() == 2
                        && list.stream().anyMatch(t -> t.batchId().equals(11L) && t.newQuantity().compareTo(new BigDecimal("20.0")) == 0)
                        && list.stream().anyMatch(t -> t.batchId().equals(13L) && t.newQuantity().compareTo(new BigDecimal("3.0")) == 0)));

        // 快照 = 扣減後的批次總和：item1 = 50 - 10 = 40；item2 = 8 - 5 = 3 (新建立)
        assertThat(snap1.getQuantity()).isEqualByComparingTo("40.0");
        List<InventorySnapshotEntity> saved = capturedSavedSnapshots();
        assertThat(saved).hasSize(2);
        assertThat(saved).anyMatch(s -> s != snap1 && s.getQuantity().compareTo(new BigDecimal("3.0")) == 0);
    }

    @Test
    @DisplayName("批次扣減 - 失敗：本店批次總和不足時整批拒絕 (即使品牌總量足夠)")
    void processBatchDeductions_Fail_InsufficientStoreStock() {
        InventoryItemEntity item1 = item(1L, "1000.0"); // 品牌總量很大
        InventoryBatchEntity b1 = batch(11L, item1, "3.0", 1); // 本店只有 3

        Map<Long, BigDecimal> deductionMap = Map.of(1L, new BigDecimal("10.0"));

        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), anySet())).thenReturn(List.of(item1));
        when(batchRepository.findAvailableBatchesForItemsForUpdate(eq(STORE_ID), anySet())).thenReturn(List.of(b1));

        assertThrows(BadRequestException.class, () ->
                inventoryService.processBatchDeductions(BRAND_ID, STORE_ID, deductionMap));

        verify(inventoryBatchDAO, never()).batchUpdateQuantities(any());
        verify(snapshotRepository, never()).saveAll(any());
    }
}
