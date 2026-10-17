package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.OrderQueryHandler;
import tw.niels.beverage_api_project.modules.order.application.handler.PlaceOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrderQuery;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderResponseDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 建立訂單的 N+1 查詢檢查。
 *
 * 修改說明：
 * - 原本呼叫已刪除的 OrderProcessFacade / OrderService，改用新的 PlaceOrderHandler
 *   （內部由 OrderItemFactory 依品牌批次查詢商品、規格、選項）。
 * - 初始狀態改為 PENDING（建立訂單僅允許 PENDING / HELD，原本送 PREPARING）。
 * - 斷言改為「1 個品項與 5 個品項的 SELECT 數量相同」：查詢數不隨品項數成長即代表沒有 N+1，
 *   比寫死精確數字更不易因無關的實作細節而失敗；另外設定合理上限。
 * - 額外驗證品項快照為真實資料、訂單號碼符合 {storeId}-{yyyyMMdd}-{seq:04d}。
 */
@Import(TestDataSourceConfig.class)
public class OrderCreationNPlusOneIntegrationTest extends AbstractIntegrationTest {

    /** 建立訂單允許的 SELECT 上限（店家 1 + 商品 1 + 規格 1，保留少量餘裕） */
    private static final long MAX_SELECTS = 4;

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;

    @Autowired private PlaceOrderHandler placeOrderHandler;
    @Autowired private OrderQueryHandler orderQueryHandler;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private StoreJpaRepository storeJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private ProductVariantJpaRepository productVariantJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long brandId;
    private Long storeId;
    private Long staffUserId;
    private record ProductInfo(Long productId, Long variantId) {}
    private final List<ProductInfo> productInfos = new ArrayList<>();

    @BeforeEach
    void setup() {
        SQLStatementCountValidator.reset();

        BrandEntity brand = new BrandEntity();
        brand.setName("Order Test Brand");
        brand = brandJpaRepository.save(brand);
        this.brandId = brand.getId();

        StoreEntity store = new StoreEntity();
        store.setBrand(brand);
        store.setName("Order Store");
        store = storeJpaRepository.save(store);
        this.storeId = store.getId();

        UserEntity staff = new UserEntity();
        staff.setBrand(brand);
        staff.setPrimaryPhone("0912345678");
        staff.setPasswordHash("hash123");
        staff = userJpaRepository.save(staff);
        this.staffUserId = staff.getId();

        CategoryEntity category = new CategoryEntity();
        category.setBrand(brand);
        category.setName("Coffee");
        categoryJpaRepository.save(category);

        for (int i = 1; i <= 5; i++) {
            ProductEntity p = new ProductEntity();
            p.setBrand(brand);
            p.setName("Latte " + i);
            p.setBasePrice(BigDecimal.valueOf(60));
            p.setStatus(ProductStatus.ACTIVE);
            p = productJpaRepository.save(p);

            ProductVariantEntity v = new ProductVariantEntity();
            v.setProduct(p);
            v.setName("Medium");
            v.setPrice(BigDecimal.valueOf(60));
            v.setSkuCode("LATTE-" + i + "-M");
            // isDeleted 預設為 false，無需手動設定
            v = productVariantJpaRepository.save(v);

            productInfos.add(new ProductInfo(p.getId(), v.getId()));
        }

        storeJpaRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("建立訂單 - OrderItemFactory 批次查詢商品，SELECT 數量不隨品項數成長")
    void testCreateOrder_ShouldBatchProductQueries() {
        // 1. 只有 1 個品項
        SQLStatementCountValidator.reset();
        Long singleItemOrderId = placeOrderHandler.handle(command(productInfos.subList(0, 1)));
        long selectsForOneItem = QueryCountHolder.getGrandTotal().getSelect();

        // 2. 5 個品項
        SQLStatementCountValidator.reset();
        Long fiveItemOrderId = placeOrderHandler.handle(command(productInfos));
        long selectsForFiveItems = QueryCountHolder.getGrandTotal().getSelect();

        // 查詢數不隨品項數成長（沒有 N+1），且在合理上限內
        assertThat(selectsForFiveItems).isEqualTo(selectsForOneItem);
        assertThat(selectsForFiveItems).isLessThanOrEqualTo(MAX_SELECTS);

        // 3. 驗證品項資料：真實快照（取代原本的 "Snapshot Name"）、訂單號碼格式
        OrderResponseDto single = orderQueryHandler.handle(new GetOrderQuery(brandId, singleItemOrderId));
        OrderResponseDto five = orderQueryHandler.handle(new GetOrderQuery(brandId, fiveItemOrderId));

        assertThat(single.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(single.getOrderNumber()).matches(storeId + "-\\d{8}-0001");
        assertThat(five.getOrderNumber()).matches(storeId + "-\\d{8}-0002");
        assertThat(five.getItems()).hasSize(5);
        assertThat(five.getItems()).allSatisfy(item -> {
            assertThat(item.getProductName()).startsWith("Latte ");
            assertThat(item.getVariantName()).isEqualTo("Medium");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("60.00");
            assertThat(item.getOptions()).isNotNull().isEmpty();
        });
        assertThat(five.getTotalAmount()).isEqualByComparingTo("300.00");
    }

    private PlaceOrderCommand command(List<ProductInfo> infos) {
        List<OrderItemDto> items = new ArrayList<>();
        for (ProductInfo info : infos) {
            items.add(new OrderItemDto(info.productId(), info.variantId(), 1, null, Collections.emptyList()));
        }
        return new PlaceOrderCommand(brandId, storeId, staffUserId, OrderStatus.PENDING, items);
    }
}
