package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;
import tw.niels.beverage_api_project.modules.order.service.OrderService;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Import(TestDataSourceConfig.class)
public class OrderCreationNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private OrderService orderService;
    @Autowired private OrderProcessFacade orderProcessFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private Long brandId;
    private Long storeId;
    private Long staffUserId;
    private List<Long> productIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        SQLStatementCountValidator.reset();

        Brand brand = new Brand();
        brand.setName("Order Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        Store store = new Store();
        store.setBrand(brand);
        store.setName("Order Store");
        store = storeRepository.save(store);
        this.storeId = store.getId();

        User staff = new User();
        staff.setBrand(brand);
        staff.setPrimaryPhone("0912345678");
        staff.setPasswordHash("hash123"); // 避免 Not Null 錯誤
        staff = userRepository.save(staff);
        this.staffUserId = staff.getId();

        Category category = new Category();
        category.setBrand(brand);
        category.setName("Coffee");
        categoryRepository.save(category);

        // 建立 5 種商品
        for (int i = 1; i <= 5; i++) {
            Product p = new Product();
            p.setBrand(brand);
            p.setName("Latte " + i);
            p.setBasePrice(BigDecimal.valueOf(60));
            p.setStatus(ProductStatus.ACTIVE);
            p.setCategories(Set.of(category));
            p = productRepository.save(p);
            productIds.add(p.getId());
        }

        // Mock 身份驗證，因為 createOrder 會檢查 user (如果是線上點餐) 或忽略 (如果是 Kiosk)
        // 這裡我們假設是 Kiosk 或未登入，具體看您的 createOrder 實作是否強制需要 user
        // 根據 OrderService.createOrder 邏輯，如果沒有 userId 傳入可能是訪客。

        storeRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("建立訂單 - 檢查 OrderItemProcessor 是否有 N+1 商品查詢")
    void testCreateOrder_ShouldBatchProductQueries() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();

        request.setStatus(OrderStatus.PREPARING);

        List<OrderItemDto> items = new ArrayList<>();
        for (Long pid : productIds) {
            OrderItemDto item = new OrderItemDto();
            item.setProductId(pid);
            item.setQuantity(1);
            item.setOptionIds(new ArrayList<>()); // 簡化：不測選項的 N+1
            items.add(item);
        }
        request.setItems(items);

        // 重置計數器
        SQLStatementCountValidator.reset();

        // 執行建立訂單
        orderProcessFacade.createOrder(brandId, storeId, staffUserId, request);

        // 斷言分析：
        // OrderItemProcessorService.java:52 在迴圈內呼叫 findByBrand_IdAndId
        // 預期：1 Store + 1 Staff + 1 UserProfile + 1 StaffProfile + 1 Batch Product = 5
        SQLStatementCountValidator.assertSelectCount(5);
    }
}