package tw.niels.beverage_api_project.integration;

import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
// 修正：Entity 類別必須加上 Entity 後綴，Repository 必須加上 Jpa 前綴
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.application.ProductCacheNames;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.service.ProductService;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestDataSourceConfig.class)
public class ProductNPlusOneIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;

    @Autowired private ProductService productService;
    @Autowired private ProductJpaRepository productRepository;
    @Autowired private BrandJpaRepository brandRepository;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private OptionGroupJpaRepository optionGroupRepository;
    @Autowired private CacheManager cacheManager;

    private Long brandId;

    @BeforeEach
    void setup() {
        // 修改：改用 ProductCacheNames 常數（與 @Cacheable／@CacheEvict 同一來源），並一併清除摘要快取
        for (String cacheName : List.of(ProductCacheNames.POS, ProductCacheNames.SUMMARY)) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        productRepository.deleteAll();
        categoryRepository.deleteAll();
        optionGroupRepository.deleteAll();
        brandRepository.deleteAll();

        // 1. 建立品牌
        BrandEntity brand = new BrandEntity();
        brand.setName("N+1 Test Brand");
        brand = brandRepository.save(brand);
        this.brandId = brand.getId();

        // 2. 建立關聯資料
        OptionGroupEntity iceGroup = new OptionGroupEntity();
        iceGroup.setBrand(brand);
        iceGroup.setName("Ice Level");
        iceGroup.setSelectionType(SelectionType.SINGLE);
        iceGroup = optionGroupRepository.save(iceGroup);

        CategoryEntity teaCategory = new CategoryEntity();
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
        // 若未優化可能會是 10 條 (1 + 3規格 + 3分類 + 3選項群組)
        SQLStatementCountValidator.assertSelectCount(1);

        // 新增：確認規格、分類、選項群組都已組進 DTO（延遲載入的集合若沒被 FETCH，上面的計數就會失敗）
        assertThat(results).allSatisfy(dto -> {
            assertThat(dto.getVariants()).hasSize(1);
            assertThat(dto.getCategories()).hasSize(1);
            assertThat(dto.getOptionGroups()).hasSize(1);
        });
    }

    private void createProduct(BrandEntity brand, String name, CategoryEntity category, OptionGroupEntity group) {
        ProductEntity p = new ProductEntity();
        p.setBrand(brand);
        p.setName(name);
        p.setBasePrice(BigDecimal.valueOf(50));
        p.setStatus(ProductStatus.ACTIVE);
        // 修改：改用可變集合（Set.of 不可變，Hibernate 管理集合時可能需要修改）
        p.setCategories(new HashSet<>(Set.of(category)));
        p.setOptionGroups(new HashSet<>(Set.of(group)));

        // 新增：每個商品加一個規格（POS 下單必須帶 variantId），一併驗證規格集合也被 JOIN FETCH
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setName("中杯");
        variant.setPrice(BigDecimal.valueOf(50));
        p.addVariant(variant);

        productRepository.save(p);
    }
}
