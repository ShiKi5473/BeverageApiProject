package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.report.entity.DailyStoreStatsEntity;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.report.service.StoreSettlementService;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestDataSourceConfig.class)
public class ReportAggregationNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private StoreSettlementService storeSettlementService;
    @Autowired private BrandJpaRepository brandRepository;
    @Autowired private StoreJpaRepository storeRepository;
    @Autowired private ProductJpaRepository productRepository;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private OrderJpaRepository orderRepository;
    @Autowired private PaymentMethodJpaRepository paymentMethodRepository;
    @Autowired private DailyStoreStatsJpaRepository dailyStoreStatsRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    // [B-7] 使用營業時區的 Clock / ZoneId，不再用 LocalDate.now() 與 ZoneId.systemDefault()
    @Autowired private Clock clock;
    @Autowired private ZoneId businessZoneId;

    private Long brandId;
    private Long storeId;
    private LocalDate targetDate;
    private PaymentMethodEntity cashPaymentMethod;
    private PaymentMethodEntity creditCardPaymentMethod;

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

        this.cashPaymentMethod = findOrCreatePayment("CASH", "Cash");
        this.creditCardPaymentMethod = findOrCreatePayment("CREDIT_CARD", "Credit Card");

        this.targetDate = LocalDate.now(clock).minusDays(1); // 結算昨天的報表

        // 2. 建立 5 種不同商品，並產生 5 筆現金訂單 (每筆買一種)
        // 這會導致商品統計有 5 列，若結算又在迴圈內逐一查詢商品就會被 SELECT 計數抓到
        for (int i = 1; i <= 5; i++) {
            ProductEntity product = new ProductEntity();
            product.setBrand(brand);
            product.setName("Drink " + i);
            product.setBasePrice(new BigDecimal("50"));
            product.setStatus(ProductStatus.ACTIVE);
            product.setCategories(Set.of(category));
            // 訂單品項需要規格 (variant)，經由 addVariant 以 cascade 一併儲存
            ProductVariantEntity variant = new ProductVariantEntity();
            variant.setName("Regular");
            variant.setPrice(new BigDecimal("50"));
            product.addVariant(variant);
            product = productRepository.save(product);

            createClosedOrder(brand, store, product, "ORD-" + i);
        }

        // [B-6] 額外訂單：信用卡 80、未指定付款方式 30 (皆 CLOSED)，以及一筆取消單 (CANCELLED，沒有 completedTime)
        createOrderWithoutItems(brand, store, "ORD-CC", OrderStatus.CLOSED, "80", creditCardPaymentMethod, true);
        createOrderWithoutItems(brand, store, "ORD-NOPAY", OrderStatus.CLOSED, "30", null, true);
        createOrderWithoutItems(brand, store, "ORD-CANCEL", OrderStatus.CANCELLED, "50", null, false);

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

        // 優化後單店結算的 SELECT 固定為 5 次，與商品 / 訂單數量無關：
        // 1. existsByStoreIdAndDate (冪等檢查)  2. 訂單狀態統計  3. 付款方式統計
        // 4. 商品銷售統計                       5. 批次查詢所有商品的分類名稱 (取代迴圈內逐一查商品)
        // 寫入 (persist) 不會產生 SELECT。若又回到迴圈內查詢，次數會隨商品數增加。
        SQLStatementCountValidator.assertSelectCount(5);
    }

    @Test
    @DisplayName("報表結算 - 取消數依 order_time、付款方式依代碼彙總 (含未指定)")
    void testProcessStoreStats_CancelledAndPaymentBreakdown() {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(LocalTime.MAX);

        storeSettlementService.processStoreStats(storeId, brandId, targetDate, start, end);

        DailyStoreStatsEntity stats = dailyStoreStatsRepository.findByStoreIdAndDate(storeId, targetDate).orElseThrow();
        // 5 筆現金 50 + 信用卡 80 + 未指定 30 = 7 筆已完成、總額 360
        assertThat(stats.getTotalOrders()).isEqualTo(7);
        assertThat(stats.getFinalRevenue()).isEqualByComparingTo("360");
        // 取消單沒有 completedTime，必須用 order_time 才算得到
        assertThat(stats.getCancelledOrders()).isEqualTo(1);
        assertThat(stats.getCashTotal()).isEqualByComparingTo("250");
        assertThat(stats.getCreditCardTotal()).isEqualByComparingTo("80");
        assertThat(stats.getThirdPartyTotal()).isEqualByComparingTo("0");
        assertThat(stats.getUnspecifiedTotal()).isEqualByComparingTo("30");
        assertThat(stats.getLinePayTotal()).isEqualByComparingTo("0");
    }

    private PaymentMethodEntity findOrCreatePayment(String code, String name) {
        return paymentMethodRepository.findAll().stream()
                .filter(pm -> code.equals(pm.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    PaymentMethodEntity pm = new PaymentMethodEntity();
                    pm.setCode(code);
                    pm.setName(name);
                    return paymentMethodRepository.save(pm);
                });
    }

    /**
     * order_time 有 @CreationTimestamp，儲存時會被覆寫成「現在」，所以存檔後以 JDBC 直接改成目標日期當天中午；
     * completed_time 同理一併設定 (取消單不設定)。
     */
    private void setTimes(OrderEntity order, boolean completed) {
        Timestamp noon = Timestamp.from(targetDate.atTime(12, 0).atZone(businessZoneId).toInstant());
        jdbcTemplate.update("UPDATE orders SET order_time = ?, completed_time = ? WHERE order_id = ?",
                noon, completed ? noon : null, order.getId());
    }

    private void createOrderWithoutItems(BrandEntity brand, StoreEntity store, String orderNo, OrderStatus status,
                                         String amount, PaymentMethodEntity payment, boolean completed) {
        OrderEntity order = new OrderEntity();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNo);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(amount));
        order.setFinalAmount(new BigDecimal(amount));
        order.setPaymentMethod(payment);
        order = orderRepository.save(order);
        setTimes(order, completed);
    }

    private void createClosedOrder(BrandEntity brand, StoreEntity store, ProductEntity product, String orderNo) {
        OrderEntity order = new OrderEntity();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNo);
        order.setStatus(OrderStatus.CLOSED); // 必須是 CLOSED 才會被統計
        order.setTotalAmount(new BigDecimal("50"));
        order.setFinalAmount(new BigDecimal("50"));
        order.setPaymentMethod(this.cashPaymentMethod);

        OrderItemEntity item = new OrderItemEntity();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductVariant(product.getVariants().iterator().next());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50"));
        item.setSubtotal(new BigDecimal("50"));

        order.getItems().add(item);
        order = orderRepository.save(order);
        setTimes(order, true);
    }
}
