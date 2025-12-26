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
import tw.niels.beverage_api_project.modules.product.entity.ProductVariant;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantRepository; // 新增注入
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
    @Autowired private ProductVariantRepository productVariantRepository; // 新增
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private Long brandId;
    private Long storeId;
    private Long staffUserId;
    // 儲存商品 ID 與對應的 規格 ID
    private record ProductInfo(Long productId, Long variantId) {}
    private List<ProductInfo> productInfos = new ArrayList<>();

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
        staff.setPasswordHash("hash123");
        staff = userRepository.save(staff);
        this.staffUserId = staff.getId();

        Category category = new Category();
        category.setBrand(brand);
        category.setName("Coffee");
        categoryRepository.save(category);

        // 建立 5 種商品，並且每種商品建立 1 個規格
        for (int i = 1; i <= 5; i++) {
            // 1. 建立商品
            Product p = new Product();
            p.setBrand(brand);
            p.setName("Latte " + i);
            p.setBasePrice(BigDecimal.valueOf(60));
            p.setStatus(ProductStatus.ACTIVE);
            p.setCategories(Set.of(category));
            p = productRepository.save(p);

            // 2. 建立規格 (Variant) - 重要修改
            ProductVariant v = new ProductVariant();
            v.setProduct(p);
            v.setName("Medium");
            v.setPrice(BigDecimal.valueOf(60));
            v.setSkuCode("LATTE-" + i + "-M");
            v.setDeleted(false);
            v = productVariantRepository.save(v);

            productInfos.add(new ProductInfo(p.getId(), v.getId()));
        }

        storeRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("建立訂單 - 檢查 OrderItemProcessor 是否有 N+1 商品查詢")
    void testCreateOrder_ShouldBatchProductQueries() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setStatus(OrderStatus.PREPARING);

        List<OrderItemDto> items = new ArrayList<>();
        // 使用 Record 建構子來建立 DTO，並填入 variantId
        for (ProductInfo info : productInfos) {
            OrderItemDto item = new OrderItemDto(
                    info.productId(),
                    info.variantId(), // 必填：規格 ID
                    1,
                    null,
                    Collections.emptyList()
            );
            items.add(item);
        }
        request.setItems(items);

        // 重置計數器
        SQLStatementCountValidator.reset();

        // 執行建立訂單
        orderProcessFacade.createOrder(brandId, storeId, staffUserId, request);

        // 斷言分析：
        // 1. SELECT Store
        // 2. SELECT Staff
        // 3. SELECT Batch Products (IN clause)
        // 4. SELECT Batch Variants (IN clause) -> 新增的查詢
        // 5. SELECT User/Staff Profiles (視 JPA fetch 設定，通常會有關聯查詢)
        // OrderItemProcessorService 現在會執行：productRepository.findByBrand_IdAndIdIn AND productVariantRepository.findBy...

        // 預期查詢數增加 1 (因為多了一次對 product_variants 的批次查詢)
        // 原本預期 5，現在改為 6。重點是查詢次數固定，不會隨 items 數量 (5) 而變成 5+N
        SQLStatementCountValidator.assertSelectCount(6);
    }
}