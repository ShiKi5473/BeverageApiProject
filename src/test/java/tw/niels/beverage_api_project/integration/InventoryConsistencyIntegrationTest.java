package tw.niels.beverage_api_project.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.dto.InventoryAuditRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatchEntity;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItemEntity;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventorySnapshotJpaRepository;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryAuditService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryDeductionService;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [B-5] 庫存三套數字一致性：進貨 → 扣減 → 盤點之後，
 * 品牌 totalQuantity、分店 Snapshot、分店批次總和三者必須相同；
 * 並驗證進貨與扣減併發時不會死鎖、結果仍一致。
 */
@Import(TestDataSourceConfig.class)
public class InventoryConsistencyIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryDeductionService deductionService;
    @Autowired private InventoryAuditService auditService;
    @Autowired private InventoryItemJpaRepository itemRepository;
    @Autowired private InventoryBatchJpaRepository batchRepository;
    @Autowired private InventorySnapshotJpaRepository snapshotRepository;
    @Autowired private BrandJpaRepository brandRepository;
    @Autowired private StoreJpaRepository storeRepository;
    @Autowired private UserJpaRepository userRepository;

    private Long brandId;
    private Long storeId;
    private Long userId;
    private Long itemId;

    @BeforeEach
    void setup() {
        BrandEntity brand = new BrandEntity();
        brand.setName("Consistency Brand");
        brand = brandRepository.save(brand);
        brandId = brand.getId();

        StoreEntity store = new StoreEntity();
        store.setBrand(brand);
        store.setName("Consistency Store");
        store = storeRepository.save(store);
        storeId = store.getId();

        UserEntity user = new UserEntity();
        user.setBrand(brand);
        user.setPrimaryPhone("0900000001");
        user.setPasswordHash("dummy_hash_for_test");
        user = userRepository.save(user);
        userId = user.getId();
        Mockito.when(helperService.getCurrentUserId()).thenReturn(userId);

        InventoryItemEntity item = new InventoryItemEntity();
        item.setBrand(brand);
        item.setName("Milk");
        item.setUnit("L");
        item = itemRepository.save(item);
        itemId = item.getId();
    }

    private void ship(String qty) {
        AddShipmentRequestDto.BatchItemDto line = new AddShipmentRequestDto.BatchItemDto();
        line.setInventoryItemId(itemId);
        line.setQuantity(new BigDecimal(qty));
        line.setExpiryDate(LocalDate.now().plusDays(30));
        AddShipmentRequestDto request = new AddShipmentRequestDto();
        request.setSupplier("S");
        request.setItems(new ArrayList<>(List.of(line)));
        inventoryService.addShipment(brandId, storeId, userId, request);
    }

    private BigDecimal batchSum() {
        return batchRepository.sumQuantityByStoreAndItem(storeId, itemId).orElse(BigDecimal.ZERO);
    }

    private BigDecimal total() {
        return itemRepository.findByBrand_IdAndId(brandId, itemId).orElseThrow().getTotalQuantity();
    }

    private BigDecimal snapshot() {
        return snapshotRepository.findByStore_IdAndInventoryItem_Id(storeId, itemId).orElseThrow().getQuantity();
    }

    @Test
    @DisplayName("進貨 → 扣減 → 盤點後，totalQuantity、Snapshot、批次總和三者一致")
    void shipDeductAudit_KeepsThreeNumbersConsistent() {
        ship("60.00");
        ship("40.00");
        assertThat(total()).isEqualByComparingTo("100.00");
        assertThat(batchSum()).isEqualByComparingTo("100.00");
        assertThat(snapshot()).isEqualByComparingTo("100.00");

        deductionService.deductInventory(brandId, storeId, itemId, new BigDecimal("30.00"));
        assertThat(total()).isEqualByComparingTo("70.00");
        assertThat(batchSum()).isEqualByComparingTo("70.00");
        assertThat(snapshot()).isEqualByComparingTo("70.00");

        // 系統數量 = 批次總和 70，實盤 65 -> 只盤損 5 (不會把已售出的 30 再扣一次)
        InventoryAuditRequestDto.AuditItemDto dto = new InventoryAuditRequestDto.AuditItemDto();
        dto.setInventoryItemId(itemId);
        dto.setActualQuantity(new BigDecimal("65.00"));
        InventoryAuditRequestDto audit = new InventoryAuditRequestDto();
        audit.setItems(new ArrayList<>(List.of(dto)));
        auditService.performAudit(brandId, storeId, audit);

        assertThat(total()).isEqualByComparingTo("65.00");
        assertThat(batchSum()).isEqualByComparingTo("65.00");
        assertThat(snapshot()).isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("進貨與扣減併發：不死鎖，結果仍一致")
    void concurrentShipAndDeduct_NoDeadlock() throws InterruptedException {
        ship("100.00");

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final boolean shipping = i % 2 == 0;
            executor.submit(() -> {
                try {
                    if (shipping) {
                        ship("10.00");
                    } else {
                        deductionService.deductInventory(brandId, storeId, itemId, new BigDecimal("5.00"));
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(failures.get()).isZero();
        // 100 + 4 次進貨 10 - 4 次扣減 5 = 120
        assertThat(batchSum()).isEqualByComparingTo("120.00");
        assertThat(total()).isEqualByComparingTo("120.00");
        assertThat(snapshot()).isEqualByComparingTo("120.00");
        List<InventoryBatchEntity> batches = batchRepository.findByStore_IdAndInventoryItem_Id(storeId, itemId);
        assertThat(batches).isNotEmpty();
    }
}
