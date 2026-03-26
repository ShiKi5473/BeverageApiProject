package tw.niels.beverage_api_project.modules.product.application.handler;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.application.command.*;
import tw.niels.beverage_api_project.modules.product.dto.ProductResponseDto;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;
import tw.niels.beverage_api_project.modules.product.service.ProductService;

import java.util.stream.Collectors;

/**
 * 商品相關命令處理器
 *
 * 處理商品、規格、分類、選項群組的建立與修改。
 *
 * 處理流程（以 CreateProduct 為例）：
 * 1. 接收 CreateProductCommand
 * 2. 委派 ProductService 執行建立邏輯（過渡期）
 * 3. 清除商品快取（ProductEntity-summary、ProductEntity-pos）
 * 4. 回傳新建商品 ID
 *
 * 備註：過渡期大部分操作委派 ProductService，待 Phase 3 改為操作 Domain Model。
 */
@Component
public class ProductCommandHandler {

    private final ProductRepository productRepository;
    private final BrandJpaRepository brandRepository;
    private final ProductService productService; // 暫時保留以重複利用其私有 build 邏輯 (若有的話)
    private final CategoryJpaRepository CategoryJpaRepository;
    private final OptionGroupJpaRepository OptionGroupJpaRepository;
    private final ProductVariantJpaRepository ProductVariantJpaRepository;

    public ProductCommandHandler(ProductRepository productRepository,
                                 BrandJpaRepository brandRepository,
                                 ProductService productService,
                                 CategoryJpaRepository CategoryJpaRepository,
                                 OptionGroupJpaRepository OptionGroupJpaRepository,
                                 ProductVariantJpaRepository ProductVariantJpaRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.productService = productService;
        this.CategoryJpaRepository = CategoryJpaRepository;
        this.OptionGroupJpaRepository = OptionGroupJpaRepository;
        this.ProductVariantJpaRepository = ProductVariantJpaRepository;
    }

    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#command.brandId()")
    public Long handle(CreateProductCommand command) {
        // 重複利用 ProductService 的邏輯 (過渡期)
        ProductResponseDto dto = productService.createProduct(command.brandId(), command.requestDto());
        return dto.getProductId();
    }

    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#command.brandId()")
    public ProductResponseDto handle(AddProductVariantCommand command) {
        return productService.addProductVariant(command.brandId(), command.productId(), command.requestDto());
    }

    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#command.brandId()")
    public void handle(UpdateProductVariantCommand command) {
        productService.updateProductVariant(command.brandId(), command.variantId(), command.requestDto());
    }

    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#command.brandId()")
    public void handle(DeleteProductVariantCommand command) {
        productService.deleteProductVariant(command.brandId(), command.variantId());
    }

    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#command.brandId()")
    public ProductEntity handle(LinkOptionGroupsCommand command) {
        return productService.linkOptionGroupsToProduct(command.brandId(), command.productId(), command.groupIds());
    }

    @Transactional
    public Long handle(CreateCategoryCommand command) {
        BrandEntity BrandEntity = brandRepository.findById(command.brandId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + command.brandId()));

        CategoryEntity CategoryEntity = new CategoryEntity();
        CategoryEntity.setBrand(BrandEntity);
        CategoryEntity.setName(command.requestDto().getName());
        CategoryEntity.setSortOrder(command.requestDto().getSortOrder() != null ? command.requestDto().getSortOrder() : 0);

        return CategoryJpaRepository.save(CategoryEntity).getId();
    }
}
