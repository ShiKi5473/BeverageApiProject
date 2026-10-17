package tw.niels.beverage_api_project.modules.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditItemResponseDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventorySnapshotEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryTransactionEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryTransactionJpaRepository;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryAuditService 單元測試。
 *
 * <p>[B-5] 涵蓋的修正：盤點時的「系統數量」= 本店批次總和 (而不是可能已與批次脫節的 Snapshot.quantity)，
 * 盤點後 Snapshot 一律對齊實盤數量；盤盈直接累加品牌總量，盤損委派批次 FIFO 扣減。</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryAuditServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 2L;
    private static final Long USER_ID = 9L;
    private static final Long ITEM_A = 100L;
    private static final Long ITEM_B = 200L;
    private static final Instant NOW = Instant.parse("2026-10-17T02:00:00Z"); // 台北 2026-10-17 10:00

    @Mock private InventoryItemJpaRepository itemRepository;
    @Mock private InventoryBatchJpaRepository batchRepository;
    @Mock private InventorySnapshotJpaRepository snapshotRepository;
    @Mock private InventoryTransactionJpaRepository transactionRepository;
    @Mock private StoreJpaRepository storeRepository;
    @Mock private UserJpaRepository userRepository;
    @Mock private ControllerHelperService helperService;
    @Mock private InventoryDeductionService deductionService;

    private InventoryAuditService auditService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Taipei"));
        auditService = new InventoryAuditService(itemRepository, batchRepository, snapshotRepository,
                transactionRepository, storeRepository, userRepository, helperService, deductionService, clock);
    }

    // ───────────── 測試資料輔助方法 ─────────────

    private InventoryItemEntity item(Long id, String total) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(id);
        item.setName("Item-" + id);
        item.setUnit("KG");
        item.setTotalQuantity(new BigDecimal(total));
        return item;
    }

    private InventorySnapshotEntity snapshot(InventoryItemEntity item, String qty) {
        InventorySnapshotEntity snapshot = new InventorySnapshotEntity();
        snapshot.setInventoryItem(item);
        snapshot.setQuantity(new BigDecimal(qty));
        return snapshot;
    }

    private InventoryBatchJpaRepository.ItemQuantitySum batchSum(Long itemId, String qty) {
        return new InventoryBatchJpaRepository.ItemQuantitySum() {
            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public BigDecimal getQuantity() {
                return new BigDecimal(qty);
            }
        };
    }

    private InventoryAuditRequestDto request(Long itemId, String actualQty) {
        InventoryAuditRequestDto.AuditItemDto dto = new InventoryAuditRequestDto.AuditItemDto();
        dto.setInventoryItemId(itemId);
        dto.setActualQuantity(new BigDecimal(actualQty));
        InventoryAuditRequestDto request = new InventoryAuditRequestDto();
        request.setNote("月底盤點");
        request.setItems(new ArrayList<>(List.of(dto)));
        return request;
    }

    private void stubStoreAndOperator() {
        StoreEntity store = new StoreEntity();
        store.setId(STORE_ID);
        when(storeRepository.findByBrand_IdAndId(BRAND_ID, STORE_ID)).thenReturn(Optional.of(store));
        when(helperService.getCurrentUserId()).thenReturn(USER_ID);
        // userRepository.findByBrand_IdAndId 未 stub：Mockito 對 Optional 回傳 Optional.empty()，operator 為 null 亦可
    }

    private <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<Iterable<T>> iterableCaptor() {
        return ArgumentCaptor.forClass((Class) Iterable.class);
    }

    // ───────────── 盤點 ─────────────

    @Test
    @DisplayName("盤點 - 系統數量取本店批次總和而非 Snapshot：Snapshot 過期但實盤與批次一致時無差異")
    void performAudit_UsesBatchSumAsSystemQuantity() {
        stubStoreAndOperator();
        InventoryItemEntity itemA = item(ITEM_A, "100.00");
        // Snapshot 已與批次脫節 (90)，批次實際總和為 40
        InventorySnapshotEntity staleSnapshot = snapshot(itemA, "90.00");

        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of(itemA));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), eq(Set.of(ITEM_A))))
                .thenReturn(List.of(staleSnapshot));
        when(batchRepository.sumQuantityByStoreAndItems(eq(STORE_ID), eq(Set.of(ITEM_A))))
                .thenReturn(List.of(batchSum(ITEM_A, "40.00")));

        auditService.performAudit(BRAND_ID, STORE_ID, request(ITEM_A, "40.00"));

        // 實盤 40 = 批次總和 40 -> 沒有差異、不產生異動紀錄、不扣庫存、不建立盤盈批次
        ArgumentCaptor<Iterable<InventoryTransactionEntity>> trxCaptor = iterableCaptor();
        verify(transactionRepository).saveAll(trxCaptor.capture());
        assertThat(toList(trxCaptor.getValue())).isEmpty();
        verify(deductionService, never()).processBatchDeductions(any(), any(), any());
        verify(batchRepository, never()).save(any());

        // Snapshot 被校正為實盤數量，並記錄盤點時間 (使用注入的 Clock)
        assertThat(staleSnapshot.getQuantity()).isEqualByComparingTo("40.00");
        assertThat(staleSnapshot.getLastCheckedAt()).isEqualTo(NOW);
        assertThat(itemA.getTotalQuantity()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("盤點 - 盤損：產生異動紀錄並委派批次 FIFO 扣減差額")
    void performAudit_Loss_DelegatesDeduction() {
        stubStoreAndOperator();
        InventoryItemEntity itemA = item(ITEM_A, "100.00");
        InventorySnapshotEntity snap = snapshot(itemA, "40.00");

        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of(itemA));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of(snap));
        when(batchRepository.sumQuantityByStoreAndItems(eq(STORE_ID), eq(Set.of(ITEM_A))))
                .thenReturn(List.of(batchSum(ITEM_A, "40.00")));

        // 系統 40，實盤 35 -> 盤損 5
        auditService.performAudit(BRAND_ID, STORE_ID, request(ITEM_A, "35.00"));

        ArgumentCaptor<Iterable<InventoryTransactionEntity>> trxCaptor = iterableCaptor();
        verify(transactionRepository).saveAll(trxCaptor.capture());
        List<InventoryTransactionEntity> transactions = toList(trxCaptor.getValue());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getChangeAmount()).isEqualByComparingTo("-5.00");
        assertThat(transactions.getFirst().getBalanceAfter()).isEqualByComparingTo("35.00");
        assertThat(transactions.getFirst().getReasonType()).isEqualTo("AUDIT");

        verify(deductionService).processBatchDeductions(eq(BRAND_ID), eq(STORE_ID), argThat(deductions ->
                deductions.size() == 1 && deductions.get(ITEM_A).compareTo(new BigDecimal("5")) == 0));
        assertThat(snap.getQuantity()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("盤點 - 盤盈：建立新批次並直接累加品牌總量")
    void performAudit_Gain_CreatesBatchAndIncreasesTotal() {
        stubStoreAndOperator();
        InventoryItemEntity itemA = item(ITEM_A, "100.00");

        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of(itemA));
        when(snapshotRepository.findByStore_IdAndInventoryItem_IdIn(eq(STORE_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of());
        when(batchRepository.sumQuantityByStoreAndItems(eq(STORE_ID), eq(Set.of(ITEM_A))))
                .thenReturn(List.of(batchSum(ITEM_A, "40.00")));

        // 系統 40，實盤 50 -> 盤盈 10
        auditService.performAudit(BRAND_ID, STORE_ID, request(ITEM_A, "50.00"));

        ArgumentCaptor<InventoryBatchEntity> batchCaptor = ArgumentCaptor.forClass(InventoryBatchEntity.class);
        verify(batchRepository).save(batchCaptor.capture());
        InventoryBatchEntity gainBatch = batchCaptor.getValue();
        assertThat(gainBatch.getCurrentQuantity()).isEqualByComparingTo("10.00");
        // [B-7] 生產日 = 營業時區的「今天」(台北 2026-10-17)，預設效期 = 今天 + 7 天
        assertThat(gainBatch.getProductionDate()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(gainBatch.getExpiryDate()).isEqualTo(LocalDate.of(2026, 10, 24));

        // 品牌總量直接在記憶體累加 (品項已被鎖定的受管實體)
        assertThat(itemA.getTotalQuantity()).isEqualByComparingTo("110.00");
        verify(deductionService, never()).processBatchDeductions(any(), any(), any());

        // 沒有既有 Snapshot -> 建立新的並對齊實盤 50
        ArgumentCaptor<Iterable<InventorySnapshotEntity>> snapCaptor = iterableCaptor();
        verify(snapshotRepository).saveAll(snapCaptor.capture());
        List<InventorySnapshotEntity> snapshots = toList(snapCaptor.getValue());
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getQuantity()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("盤點 - 品項不存在或不屬於該品牌時拒絕")
    void performAudit_Fail_WhenItemNotInBrand() {
        stubStoreAndOperator();
        when(itemRepository.findByBrandIdAndIdInForUpdate(eq(BRAND_ID), eq(Set.of(ITEM_A)))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> auditService.performAudit(BRAND_ID, STORE_ID, request(ITEM_A, "1.00")));
        verify(transactionRepository, never()).saveAll(any());
    }

    // ───────────── 盤點清單 ─────────────

    @Test
    @DisplayName("盤點清單 - 數量來自本店批次總和，沒有批次的品項為 0")
    void getAuditList_UsesBatchSum() {
        InventoryItemEntity itemA = item(ITEM_A, "999.00"); // 品牌總量與盤點清單無關
        InventoryItemEntity itemB = item(ITEM_B, "0.00");
        when(itemRepository.findByBrand_Id(BRAND_ID)).thenReturn(List.of(itemA, itemB));
        when(batchRepository.sumQuantityByStore(STORE_ID)).thenReturn(List.of(batchSum(ITEM_A, "12.50")));

        List<InventoryAuditItemResponseDto> list = auditService.getAuditList(BRAND_ID, STORE_ID);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).id()).isEqualTo(ITEM_A);
        assertThat(list.get(0).name()).isEqualTo("Item-100");
        assertThat(list.get(0).unit()).isEqualTo("KG");
        assertThat(list.get(0).quantity()).isEqualByComparingTo("12.50");
        assertThat(list.get(1).id()).isEqualTo(ITEM_B);
        assertThat(list.get(1).quantity()).isEqualByComparingTo("0");
    }
}
