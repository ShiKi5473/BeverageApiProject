package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // 修改這裡
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.service.ProductService;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestDataSourceConfig.class)
public class ProductNPlusOneIntegrationTest extends AbstractIntegrationTest {

    // 修改：使用 @MockitoBean
    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OptionGroupRepository optionGroupRepository;
    @Autowired private CacheManager cacheManager;

    private Long brandId;

    @BeforeEach
    void setup() {
        if (cacheManager.getCache("product-pos") != null) {
            cacheManager.getCache("product-pos").clear();
        }

        productRepository.deleteAll();
        categoryRepository.deleteAll();
        optionGroupRepository.deleteAll();
        brandRepository.deleteAll();

        // 1. 建立品牌
        Brand brand = new Brand();
        brand.setName("N+1 Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        // 2. 建立關聯資料
        OptionGroup iceGroup = new OptionGroup();
        iceGroup.setBrand(brand);
        iceGroup.setName("Ice Level");
        iceGroup.setSelectionType(SelectionType.SINGLE);
        iceGroup = optionGroupRepository.save(iceGroup);

        Category teaCategory = new Category();
        teaCategory.setBrand(brand);
        teaCategory.setName("Tea");
        teaCategory = categoryRepository.save(teaCategory);

        // 3. 建立多筆商品
        createProduct(brand, "Black Tea", teaCategory, iceGroup);
        createProduct(brand, "Green Tea", teaCategory, iceGroup);
        createProduct(brand, "Oolong Tea", teaCategory, iceGroup);

        productRepository.flush();
        SQLStatementCountValidator.reset();
    }

    @Test
    @DisplayName("POS 商品列表查詢 - 檢查 Lazy Loading 導致的 N+1")
    void testGetPosProducts_ShouldAvoidNPlusOne() {
        List<ProductPosDto> results = productService.getAvailableProductsForPos(brandId);
        assertThat(results).hasSize(3);

        // 預期 1 條 Select (如果已使用 JOIN FETCH)
        // 若未優化可能會是 7 條 (1 + 3分類 + 3選項群組)
        SQLStatementCountValidator.assertSelectCount(1);
    }

    private void createProduct(Brand brand, String name, Category category, OptionGroup group) {
        Product p = new Product();
        p.setBrand(brand);
        p.setName(name);
        p.setBasePrice(BigDecimal.valueOf(50));
        p.setStatus(ProductStatus.ACTIVE);
        p.setCategories(Set.of(category));
        p.setOptionGroups(Set.of(group));
        productRepository.save(p);
    }
}