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
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.report.service.ReportAggregationService;
import tw.niels.beverage_api_project.modules.report.service.StoreSettlementService;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

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
    @Autowired private BrandJpaRepository brandRepository;
    @Autowired private StoreJpaRepository storeRepository;
    @Autowired private ProductJpaRepository productRepository;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private OrderJpaRepository orderRepository;
    @Autowired private PaymentMethodJpaRepository paymentMethodRepository;

    private Long brandId;
    private Long storeId;
    private LocalDate targetDate;
    private PaymentMethodEntity cashPaymentMethod;

    @BeforeEach
    void setup() {
        SQLStatementCountValidator.reset();

        // 1. 基礎資料
        BrandEntity brand = new BrandEntity();
        brand.setName("Report Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        StoreEntity store = new StoreEntity();
        store.setBrand(brand);
        store.setName("Report Store");
        store = storeRepository.save(store);
        this.storeId = store.getId();

        CategoryEntity category = new CategoryEntity();
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
            ProductEntity product = new ProductEntity();
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
        // 5 種商品 = 5 次 Select (ProductEntity) + 5 次 Select (CategoryEntity, 因為 lazy loading)
        // 加上前面的統計查詢 3 次 (Status, Payment, ProductStats) + Insert 報表
        // 總 Select 預計會超過 10 次。

        // 優化後目標：應該只需要 3 次統計查詢 + 1 次批次查詢所有商品 (findByBrandIdAndIdIn)
        // 我們設定閾值為 4，如果超過表示 N+1 存在
        SQLStatementCountValidator.assertSelectCount(4);
    }

    private void createClosedOrder(BrandEntity brand, StoreEntity store, ProductEntity product, String orderNo) {
        OrderEntity order = new OrderEntity();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNo);
        order.setStatus(OrderStatus.CLOSED); // 必須是 CLOSED 才會被統計
        order.setOrderTime(Date.from(targetDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));
        order.setTotalAmount(new BigDecimal("50"));
        order.setFinalAmount(new BigDecimal("50"));
        order.setPaymentMethod(this.cashPaymentMethod);

        OrderItemEntity item = new OrderItemEntity();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50"));
        item.setSubtotal(new BigDecimal("50"));
        // 這裡設定 ProductSnapshot 是為了解決 N+1

        order.getItems().add(item);
        orderRepository.save(order);
    }
}