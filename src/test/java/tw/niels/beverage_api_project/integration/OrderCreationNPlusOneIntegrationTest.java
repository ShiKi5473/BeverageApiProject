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
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.facade.OrderProcessFacade;
import tw.niels.beverage_api_project.modules.order.service.OrderService;
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
import java.util.Set;

@Import(TestDataSourceConfig.class)
public class OrderCreationNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;
    @MockitoBean private ControllerHelperService helperService;

    @Autowired private OrderService orderService;
    @Autowired private OrderProcessFacade orderProcessFacade;
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
    private List<ProductInfo> productInfos = new ArrayList<>();

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
            v.setIsDeleted(false);
            v = productVariantJpaRepository.save(v);

            productInfos.add(new ProductInfo(p.getId(), v.getId()));
        }

        storeJpaRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("建立訂單 - 檢查 OrderItemProcessor 是否有 N+1 商品查詢")
    void testCreateOrder_ShouldBatchProductQueries() {
        CreateOrderRequestDto request = new CreateOrderRequestDto();
        request.setStatus(OrderStatus.PREPARING);

        List<OrderItemDto> items = new ArrayList<>();
        for (ProductInfo info : productInfos) {
            OrderItemDto item = new OrderItemDto(
                    info.productId(),
                    info.variantId(),
                    1,
                    null,
                    Collections.emptyList()
            );
            items.add(item);
        }
        request.setItems(items);

        SQLStatementCountValidator.reset();

        orderProcessFacade.createOrder(brandId, storeId, staffUserId, request);

        // 預期查詢數
        SQLStatementCountValidator.assertSelectCount(6);
    }
}
