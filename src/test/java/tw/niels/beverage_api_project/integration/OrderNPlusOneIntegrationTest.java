package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean; // 引入 MockBean
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.service.OrderService;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestDataSourceConfig.class)
public class OrderNPlusOneIntegrationTest extends AbstractIntegrationTest {

    // --- 關鍵修改：Mock 掉背景服務，防止它們執行 SQL 干擾計數 ---
    @MockitoBean
    private DataSeeder dataSeeder; // 禁止啟動時塞資料

    @MockitoBean
    private ReportRecoveryRunner reportRecoveryRunner; // 禁止檢查報表

    @MockitoBean
    private ReportScheduler reportScheduler; // 禁止排程報表
    // -------------------------------------------------------

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long brandId;
    private Long storeId;

    @BeforeEach
    void setup() {
        // 先重置計數器，確保乾淨
        SQLStatementCountValidator.reset();

        orderRepository.deleteAll();
        productRepository.deleteAll();
        storeRepository.deleteAll();
        brandRepository.deleteAll();

        Brand brand = new Brand();
        brand.setName("Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        Store store = new Store();
        store.setName("Test Store");
        store.setBrand(brand);
        store = storeRepository.save(store);
        this.storeId = store.getId();

        Product product = new Product();
        product.setName("Milk Tea");
        product.setBrand(brand);
        product.setBasePrice(new BigDecimal("50"));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        // 建立 3 筆訂單
        createOrder(brand, store, product, "ORD-001");
        createOrder(brand, store, product, "ORD-002");
        createOrder(brand, store, product, "ORD-003");

        // 強制寫入 DB，確保 setup 階段的 SQL 不會算在測試階段
        orderRepository.flush();

        // 重要：再次重置，因為上面的 save() 產生了 SQL
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("測試查詢訂單列表時不應產生 N+1 問題")
    void testGetOrders_ShouldNotProduceNPlusOne() {
        System.out.println("========== 測試開始：執行查詢 ==========");

        // 1. 執行業務邏輯
        List<Order> orders = orderService.getOrders(brandId, storeId, Optional.empty());

        // 2. 觸發 Lazy Loading (存取關聯物件)
        // 驗證是否因為存取 product 而產生額外查詢
        assertThat(orders).hasSize(3);
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                // 如果沒有 Fetch Join Product，這裡會觸發 SQL
                item.getProduct().getName();
            }
        }

        System.out.println("========== 測試結束：準備斷言 ==========");

        // 3. 斷言 SQL 數量
        // 預期：1 條 (SELECT orders left join items left join products ...)
        try {
            SQLStatementCountValidator.assertSelectCount(1);
        } catch (Exception e) {
            // 如果失敗，印出失敗訊息以便除錯
            System.err.println("N+1 檢測失敗: " + e.getMessage());
            throw e;
        }
    }

    private void createOrder(Brand brand, Store store, Product product, String orderNumber) {
        Order order = new Order();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("100"));
        order.setFinalAmount(new BigDecimal("100"));

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50"));
        item.setSubtotal(new BigDecimal("100"));

        order.getItems().add(item);

        orderRepository.save(order);
    }
}