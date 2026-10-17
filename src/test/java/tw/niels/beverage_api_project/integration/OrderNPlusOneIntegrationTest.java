package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
// Entity 類別使用 Entity 後綴，Spring Data Repository 使用 JpaRepository 後綴
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.application.handler.OrderQueryHandler;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrdersQuery;
import tw.niels.beverage_api_project.modules.order.dto.OrderResponseDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 訂單列表查詢的 N+1 檢查。
 *
 * 修改說明：
 * - 原本呼叫已刪除的 OrderService.getOrders，改用 CQRS 讀取端 OrderQueryHandler（直接由 OrderEntity 組 DTO）。
 * - 列表分兩步查詢（先取 ID、再以 entity graph 一次載入明細），預期 SELECT 數為 2，且不隨訂單數成長。
 * - 舊式（沒有商品快照）的訂單也必須能讀出：商品名稱以關聯的商品名稱備援，options 為空陣列而非 null。
 * - 移除 setup 中的 deleteAll()：AbstractIntegrationTest 每個測試結束都會 TRUNCATE。
 */
@Import(TestDataSourceConfig.class)
public class OrderNPlusOneIntegrationTest extends AbstractIntegrationTest {

    // --- Mock 掉背景服務，防止它們執行 SQL 干擾計數 ---
    @MockitoBean
    private DataSeeder dataSeeder; // 禁止啟動時塞資料

    @MockitoBean
    private ReportRecoveryRunner reportRecoveryRunner; // 禁止檢查報表

    @MockitoBean
    private ReportScheduler reportScheduler; // 禁止排程報表
    // -------------------------------------------------------

    @Autowired
    private OrderQueryHandler orderQueryHandler;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private BrandJpaRepository brandRepository;

    @Autowired
    private StoreJpaRepository storeRepository;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private ProductVariantJpaRepository productVariantRepository;

    private Long brandId;
    private Long storeId;

    @BeforeEach
    void setup() {
        // 先重置計數器，確保乾淨
        SQLStatementCountValidator.reset();

        BrandEntity brand = new BrandEntity();
        brand.setName("Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        StoreEntity store = new StoreEntity();
        store.setName("Test Store");
        store.setBrand(brand);
        store = storeRepository.save(store);
        this.storeId = store.getId();

        ProductEntity product = new ProductEntity();
        product.setName("Milk Tea");
        product.setBrand(brand);
        product.setBasePrice(new BigDecimal("50"));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProduct(product);
        variant.setName("Large");
        variant.setPrice(new BigDecimal("50"));
        variant.setSkuCode("MILK-TEA-L");
        variant = productVariantRepository.save(variant);

        // 建立 3 筆「舊式」訂單（沒有商品快照，模擬 V5 之前的資料）
        createLegacyOrder(brand, store, product, variant, storeId + "-20250101-0001");
        createLegacyOrder(brand, store, product, variant, storeId + "-20250101-0002");
        createLegacyOrder(brand, store, product, variant, storeId + "-20250101-0003");

        // 強制寫入 DB，確保 setup 階段的 SQL 不會算在測試階段
        orderRepository.flush();

        // 重要：再次重置，因為上面的 save() 產生了 SQL
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("測試查詢訂單列表時不應產生 N+1 問題（舊訂單沒有快照也能讀取）")
    void testGetOrders_ShouldNotProduceNPlusOne() {
        // 1. 執行查詢（預設最近 24 小時；DTO 組裝時會存取品項、商品、規格、選項、付款方式）
        List<OrderResponseDto> orders = orderQueryHandler.handle(
                new GetOrdersQuery(brandId, storeId, null, null, null));

        // 2. 驗證結果
        assertThat(orders).hasSize(3);
        for (OrderResponseDto order : orders) {
            assertThat(order.getItems()).hasSize(1);
            OrderResponseDto.OrderItemResponseDto item = order.getItems().get(0);
            // 沒有快照時以關聯的商品 / 規格名稱備援
            assertThat(item.getProductName()).isEqualTo("Milk Tea");
            assertThat(item.getVariantName()).isEqualTo("Large");
            // options 永不為 null（KDS 依賴）
            assertThat(item.getOptions()).isNotNull().isEmpty();
        }

        // 3. 斷言 SQL 數量：1 條取 ID（含筆數上限）+ 1 條以 entity graph 載入明細
        SQLStatementCountValidator.assertSelectCount(2);
    }

    @Test
    @DisplayName("狀態篩選：只回傳指定狀態的訂單")
    void testGetOrders_FilterByStatus() {
        List<OrderResponseDto> pending = orderQueryHandler.handle(
                new GetOrdersQuery(brandId, storeId, OrderStatus.PENDING, null, null));
        List<OrderResponseDto> closed = orderQueryHandler.handle(
                new GetOrdersQuery(brandId, storeId, OrderStatus.CLOSED, null, null));

        assertThat(pending).hasSize(3);
        assertThat(closed).isEmpty();
    }

    private void createLegacyOrder(BrandEntity brand, StoreEntity store, ProductEntity product,
                                   ProductVariantEntity variant, String orderNumber) {
        OrderEntity order = new OrderEntity();
        order.setBrand(brand);
        order.setStore(store);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("100"));
        order.setFinalAmount(new BigDecimal("100"));

        OrderItemEntity item = new OrderItemEntity();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50"));
        item.setSubtotal(new BigDecimal("100"));
        // 刻意不設定 productSnapshot（舊資料）

        order.getItems().add(item);

        orderRepository.save(order);
    }
}
