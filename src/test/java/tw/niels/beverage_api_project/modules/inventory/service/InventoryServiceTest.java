package tw.niels.beverage_api_project.modules.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshotEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.PurchaseShipmentJpaRepository;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryService (進貨 / 查詢庫存) 單元測試。
 *
 * <p>[B-5] 涵蓋的修正：進貨會同步增加品牌層級 InventoryItem.totalQuantity，
 * 且先以「依 id 排序的悲觀鎖」鎖定品項 (findByBrandIdAndIdInForUpdate)，避免與訂單扣減交錯而死鎖。</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 2L;
    private static final Long USER_ID = 3L;
    private static final Long ITEM_A = 100L;
    private static final Long ITEM_B = 200L;

    @Mock private InventoryItemJpaRepository itemRepository;
    @Mock private InventoryBatchJpaRepository batchRepository;
    @Mock private PurchaseShipmentJpaRepository shipmentRepository;
    @Mock private StoreJpaRepository storeRepository;
    @Mock private UserJpaRepository userRepository;
    @Mock private InventorySnapshotJpaRepository snapshotRepository;
    @Mock private InventoryTransactionJpaRepository transactionRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        // [B-7] 固定的營業時區時鐘，不依賴 JVM 預設時區
        Clock clock = Clock.fixed(Instant.parse("2026-10-17T02:00:00Z"), ZoneId.of("Asia/Taipei"));
        inventoryService = new InventoryService(itemRepository, batchRepository, shipmentRepository,
                storeRepository, userRepository, snapshotRepository, transactionRepository, clock);
    }

    // ───────────── 測試資料輔助方法 ─────────────

    private InventoryItemEntity item(Long id, String total) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(id);
        item.setName("Item-" + id);
        item.setTotalQuantity(new BigDecimal(total));
        return item;
    }

    private InventorySnapshotEntity snapshot(InventoryItemEntity item, String qty) {
        InventorySnapshotEntity snapshot = new InventorySnapshotEntity();
        snapshot.setInventoryItem(item);
        snapshot.setQuantity(new BigDecimal(qty));
        return snapshot;
    }

    private AddShipmentRequestDto.BatchItemDto line(Long itemId, String qty) {
        AddShipmentRequestDto.BatchItemDto dto = new AddShipmentRequestDto.BatchItemDto();
        dto.setInventoryItemId(itemId);
        dto.setQuantity(new BigDecimal(qty));
        dto.setExpiryDate(LocalDate.of(2026, 12, 31));
        return dto;
    }

    private <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<Iterable<T>> iterableCaptor() {
        return ArgumentCaptor.forClass((Class) Iterable.class);
    }

    private void stubStoreAndUser() {
        StoreEntity store = new StoreEntity();
        store.setId(STORE_ID);
        UserEntity staff = new UserEntity();
        staff.setId(USER_ID);
        when(storeRepository.findByBrand_IdAndId(BRAND_ID, STORE_ID)).thenReturn(Optional.of(store));
        when(userRepository.findByBrand_IdAndId(BRAND_ID, USER_ID)).thenReturn(Optional.of(staff));
        when(shipmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ───────────── 進貨 ─────────────

    @Test
    @DisplayName("進貨 - 同步增加品牌總量、Snapshot，並先鎖定品項 (同一品項多筆會彙總)")
    void addShipment_IncreasesTotalQuantityAndSnapshots() {
        stubStoreAndUser();

        InventoryItemEntity itemA = item(ITEM_A, "5.00");
        InventoryItemEntity itemB = item(ITEM_B, "0.00");
        InventorySnapshotEntity snapA = snapshot(itemA, "5.00"); // ITEM_B 沒有快照 -> 應建立

        // [B-5] 必須使用「加鎖」版本的批次查詢
        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A, ITEM_B))))
                .thenReturn(List.of(itemA, itemB));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), eq(Set.of(ITEM_A, ITEM_B))))
                .thenReturn(List.of(snapA));

        AddShipmentRequestDto request = new AddShipmentRequestDto();
        request.setSupplier("Supplier A");
        // 同一品項 A 出現兩次 (60 + 40)，另有品項 B (10)
        request.setItems(new ArrayList<>(List.of(line(ITEM_A, "60.00"), line(ITEM_A, "40.00"), line(ITEM_B, "10.00"))));

        inventoryService.addShipment(BRAND_ID, STORE_ID, USER_ID, request);

        // 品牌層級 totalQuantity：A = 5 + 100 = 105；B = 0 + 10 = 10
        assertThat(itemA.getTotalQuantity()).isEqualByComparingTo("105.00");
        assertThat(itemB.getTotalQuantity()).isEqualByComparingTo("10.00");
        verify(itemRepository).saveAll(any());
        // 不再使用沒有加鎖的查詢
        verify(itemRepository, never()).findByBrand_IdAndIdIn(any(), any());

        // 快照：A = 5 + 100 = 105；B 新建立 = 10
        assertThat(snapA.getQuantity()).isEqualByComparingTo("105.00");
        ArgumentCaptor<Iterable<InventorySnapshotEntity>> snapCaptor = iterableCaptor();
        verify(snapshotRepository).saveAll(snapCaptor.capture());
        List<InventorySnapshotEntity> savedSnapshots = toList(snapCaptor.getValue());
        assertThat(savedSnapshots).hasSize(2);
        assertThat(savedSnapshots).anyMatch(s -> s != snapA && s.getQuantity().compareTo(new BigDecimal("10.00")) == 0);

        // 三筆批次，剩餘量 = 進貨量
        ArgumentCaptor<Iterable<InventoryBatchEntity>> batchCaptor = iterableCaptor();
        verify(batchRepository).saveAll(batchCaptor.capture());
        List<InventoryBatchEntity> batches = toList(batchCaptor.getValue());
        assertThat(batches).hasSize(3);
        assertThat(batches).allSatisfy(b -> assertThat(b.getCurrentQuantity()).isEqualByComparingTo(b.getQuantityReceived()));

        // 三筆異動紀錄：A 的餘額依序 65 -> 105，B = 10
        ArgumentCaptor<Iterable<InventoryTransactionEntity>> trxCaptor = iterableCaptor();
        verify(transactionRepository).saveAll(trxCaptor.capture());
        List<InventoryTransactionEntity> transactions = toList(trxCaptor.getValue());
        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getBalanceAfter()).isEqualByComparingTo("65.00");
        assertThat(transactions.get(1).getBalanceAfter()).isEqualByComparingTo("105.00");
        assertThat(transactions.get(2).getBalanceAfter()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("進貨 - 品項不存在或不屬於該品牌時整筆拒絕")
    void addShipment_Fail_WhenItemNotInBrand() {
        stubStoreAndUser();

        InventoryItemEntity itemA = item(ITEM_A, "5.00");
        // 鎖定查詢只找得到 A，B 不屬於此品牌
        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A, ITEM_B))))
                .thenReturn(List.of(itemA));

        AddShipmentRequestDto request = new AddShipmentRequestDto();
        request.setItems(new ArrayList<>(List.of(line(ITEM_A, "1.00"), line(ITEM_B, "1.00"))));

        assertThrows(ResourceNotFoundException.class,
                () -> inventoryService.addShipment(BRAND_ID, STORE_ID, USER_ID, request));

        verify(batchRepository, never()).saveAll(any());
        assertThat(itemA.getTotalQuantity()).isEqualByComparingTo("5.00");
    }

    // ───────────── 查詢庫存 ─────────────

    @Test
    @DisplayName("查詢庫存 - 以本店批次總和為準")
    void getCurrentStock_UsesBatchSum() {
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_A)).thenReturn(Optional.of(new BigDecimal("42.00")));

        assertThat(inventoryService.getCurrentStock(STORE_ID, ITEM_A)).isEqualByComparingTo("42.00");
        verify(snapshotRepository, never()).findByStore_IdAndInventoryItem_Id(any(), any());
    }

    @Test
    @DisplayName("查詢庫存 - 沒有批次時退回 Snapshot，皆無則為 0")
    void getCurrentStock_FallsBackToSnapshotThenZero() {
        InventorySnapshotEntity snap = snapshot(item(ITEM_A, "0"), "7.00");
        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_A)).thenReturn(Optional.empty());
        when(snapshotRepository.findByStore_IdAndInventoryItem_Id(STORE_ID, ITEM_A)).thenReturn(Optional.of(snap));
        assertThat(inventoryService.getCurrentStock(STORE_ID, ITEM_A)).isEqualByComparingTo("7.00");

        when(batchRepository.sumQuantityByStoreAndItem(STORE_ID, ITEM_B)).thenReturn(Optional.empty());
        when(snapshotRepository.findByStore_IdAndInventoryItem_Id(STORE_ID, ITEM_B)).thenReturn(Optional.empty());
        assertThat(inventoryService.getCurrentStock(STORE_ID, ITEM_B)).isEqualByComparingTo("0");
    }
}
