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
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;
import tw.niels.beverage_api_project.modules.report.service.StoreSettlementService;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;

@Import(TestDataSourceConfig.class)
public class ReportAggregationNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private ReportAggregationService reportAggregationService;
    @Autowired private StoreSettlementService storeSettlementService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;

    private Long brandId;
    private Long storeId;
    private LocalDate targetDate;
    private PaymentMethodEntity cashPaymentMethod;

    @BeforeEach
    void setup() {
        SQLStatementCountValidator.reset();

        // 1. 基礎資料
        Brand brand = new Brand();
        brand.setName("Report Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        Store store = new Store();
        store.setBrand(brand);
        store.setName("Report Store");
        store = storeRepository.save(store);
        this.storeId = store.getId();

        Category category = new Category();
        category.setBrand(brand);
        category.setName("Tea");
        categoryRepository.save(category);

        this.cashPaymentMethod = paymentMethodRepository.findAll().stream()
                .filter(pm -> "CASH".equals(pm.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    PaymentMethodEntity pm = new PaymentMethodEntity();
                    pm.setCode("CASH");
                    pm.setName("Cash");
                    return paymentMethodRepository.save(pm);
                });

        this.targetDate = LocalDate.now().minusDays(1); // 結算昨天的報表

        // 2. 建立 5 種不同商品，並產生 5 筆訂單 (每筆買一種)
        // 這會導致 processStoreStats 中的迴圈跑 5 次
        for (int i = 1; i <= 5; i++) {
            Product product = new Product();
            product.setBrand(brand);
            product.setName("Drink " + i);
            product.setBasePrice(new BigDecimal("50"));
            product.setStatus(ProductStatus.ACTIVE);
            product.setCategories(Set.of(category));
            product = productRepository.save(product);

            createClosedOrder(brand, store, product, "ORD-" + i);
        }

        orderRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("報表結算 - 檢查是否在迴圈中查詢商品資料 (N+1)")
    void testProcessStoreStats_ShouldAvoidLoopQueries() {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(LocalTime.MAX);

        // 重置計數器
        SQLStatementCountValidator.reset();

        // 執行單店結算
        storeSettlementService.processStoreStats(storeId, brandId, targetDate, start, end);

        // 斷言分析：
        // 目前程式碼 (ReportAggregationService.java:202) 在迴圈內呼叫 findByBrand_IdAndId
        // 5 種商品 = 5 次 Select (Product) + 5 次 Select (Category, 因為 lazy loading)
        // 加上前面的統計查詢 3 次 (Status, Payment, ProductStats) + Insert 報表
        // 總 Select 預計會超過 10 次。

        // 優化後目標：應該只需要 3 次統計查詢 + 1 次批次查詢所有商品 (findByBrandIdAndIdIn)
        // 我們設定閾值為 4，如果超過表示 N+1 存在
        SQLStatementCountValidator.assertSelectCount(4);
    }

    private void createClosedOrder(Brand brand, Store store, Product product, String orderNo) {
        Order order = new Order();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNo);
        order.setStatus(OrderStatus.CLOSED); // 必須是 CLOSED 才會被統計
        order.setOrderTime(Date.from(targetDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));
        order.setTotalAmount(new BigDecimal("50"));
        order.setFinalAmount(new BigDecimal("50"));
        order.setPaymentMethod(this.cashPaymentMethod);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50"));
        item.setSubtotal(new BigDecimal("50"));
        // 這裡設定 ProductSnapshot 是為了模擬真實情況，但報表統計是關聯 Product ID

        order.getItems().add(item);
        orderRepository.save(order);
    }
}