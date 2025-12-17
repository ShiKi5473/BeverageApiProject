package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
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
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.inventory.dto.AddShipmentRequestDto;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Import(TestDataSourceConfig.class)
public class InventoryShipmentNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean
    private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryItemRepository itemRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;

    private Long brandId;
    private Long storeId;
    private List<Long> itemIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        SQLStatementCountValidator.reset();

        // 1. 基礎資料
        Brand brand = new Brand();
        brand.setName("Inv Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        Store store = new Store();
        store.setBrand(brand);
        store.setName("Inv Store");
        store = storeRepository.save(store);
        this.storeId = store.getId();

        User user = new User();
        user.setBrand(brand);
        user.setPrimaryPhone("0900000000");
        user.setPasswordHash("dummy_hash_for_test");
        user = userRepository.save(user);

        // Mock User ID
        Mockito.when(helperService.getCurrentUserId()).thenReturn(user.getId());

        // 2. 建立 5 個原物料 (Item)
        for (int i = 0; i < 5; i++) {
            InventoryItem item = new InventoryItem();
            item.setBrand(brand);
            item.setName("Item-" + i);
            item.setUnit("KG");
            item = itemRepository.save(item);
            itemIds.add(item.getId());
        }

        itemRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("庫存進貨 - 檢查迴圈查詢導致的 N+1")
    void testAddShipment_ShouldBatchOperations() {
        // 準備請求：一次進貨 5 種不同商品
        AddShipmentRequestDto request = new AddShipmentRequestDto();
        request.setSupplier("Supplier A");
        request.setNotes("Batch Restock");

        List<AddShipmentRequestDto.BatchItemDto> items = new ArrayList<>();
        for (Long itemId : itemIds) {
            AddShipmentRequestDto.BatchItemDto dto = new AddShipmentRequestDto.BatchItemDto();
            dto.setInventoryItemId(itemId);
            dto.setQuantity(new BigDecimal("10.0"));
            dto.setExpiryDate(LocalDate.now().plusDays(30));
            items.add(dto);
        }
        request.setItems(items);

        // 重置計數器
        SQLStatementCountValidator.reset();

        // 執行進貨
        inventoryService.addShipment(brandId, storeId, request);

        // 斷言分析：
        // 目前實作 (InventoryService.java) 是跑迴圈：
        // 1. findByBrandIdAndIdForUpdate (Select Item) -> 5 次
        // 2. batchRepository.save (Insert Batch) -> 5 次
        // 3. snapshotRepository.findBy... (Select Snapshot) -> 5 次
        // 4. snapshotRepository.save (Insert/Update Snapshot) -> 5 次
        // 5. transactionRepository.save (Insert Trx) -> 5 次
        // 加上 Store/User 查詢 2 次，Insert Shipment 1 次

        // 總 Select 預估：2 (Store/User) + 5 (Item) + 5 (Snapshot) = 12 次
        // 如果我們優化成批次查詢，應該只需要：
        // 2 (Store/User) + 1 (Items where id IN) + 1 (Snapshots where id IN) = 4 次

        // 這裡我們設定一個較嚴格的標準，如果超過 6 次 Select 就報錯 (迫使您優化迴圈)
        // 您可以先執行看它報錯多少，通常會是 10+ 次
        SQLStatementCountValidator.assertSelectCount(6);
    }
}