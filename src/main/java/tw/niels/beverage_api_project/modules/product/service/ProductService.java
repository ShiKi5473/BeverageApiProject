package tw.niels.beverage_api_project.modules.product.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict; // 新增
import org.springframework.cache.annotation.Cacheable; // 新增
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.dto.*;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariant;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantRepository;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final ProductVariantRepository productVariantRepository;

    public ProductService(ProductRepository productRepository,
                          BrandRepository brandRepository,
                          CategoryRepository categoryRepository,
                          OptionGroupRepository optionGroupRepository,
                          ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.productVariantRepository = productVariantRepository;
    }

    /**
     * 建立商品，並自動建立預設規格 (Default Variant)
     */
    @Transactional
    @CacheEvict(value = {"product-summary", "product-pos"}, key = "#brandId")
    public ProductResponseDto createProduct(Long brandId, CreateProductRequestDto request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("找不到品牌，ID：" + brandId));

        // 1. 驗證與設定分類
        Set<Category> categories = categoryRepository.findByBrand_IdAndIdIn(brandId, request.getCategoryIds());
        if (categories.size() != request.getCategoryIds().size()) {
            throw new RuntimeException("部分分類 ID 無效");
        }

        // 2. 驗證與設定選項群組
        Set<OptionGroup> optionGroups = new HashSet<>();
        if (request.getOptionGroupIds() != null && !request.getOptionGroupIds().isEmpty()) {
            optionGroups = request.getOptionGroupIds().stream()
                    .map(groupId -> optionGroupRepository
                            .findByBrand_IdAndId(brandId, groupId)
                            .orElseThrow(() -> new BadRequestException(
                                    "無效的選項群組 ID：" + groupId + " 或不屬於此品牌")))
                    .collect(Collectors.toSet());
        }

        // 3. 建立並儲存 Product (主檔)
        Product newProduct = new Product();
        newProduct.setBrand(brand);
        newProduct.setName(request.getName());
        newProduct.setDescription(request.getDescription());
        newProduct.setBasePrice(request.getBasePrice());
        newProduct.setImageUrl(request.getImageUrl());
        newProduct.setStatus(request.getStatus());
        newProduct.setCategories(categories);
        newProduct.setOptionGroups(optionGroups);

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            BigDecimal minPrice = null;

            for (CreateProductVariantDto variantDto : request.getVariants()) {
                ProductVariant variant = new ProductVariant();

                // Record 取值直接用 .name() 而不是 .getName()
                variant.setName(variantDto.name());
                variant.setPrice(variantDto.price());
                variant.setSkuCode(variantDto.skuCode());

                // 利用 Entity 的 helper method 建立雙向關聯
                newProduct.addVariant(variant);

                // 計算最低價格
                if (minPrice == null || variant.getPrice().compareTo(minPrice) < 0) {
                    minPrice = variant.getPrice();
                }
            }
            // 設定 Base Price 為最低規格價
            newProduct.setBasePrice(minPrice);
        } else {
            // 🛑 防呆策略：如果沒傳規格，是否要建立一個預設規格？
            // 建議：若前端沒傳 variants，強制建立一個 "預設" 規格，避免後續 Recipe 關聯出錯
            if (request.getBasePrice() == null) {
                throw new BadRequestException("若未指定規格，則必須填寫基本售價");
            }

            ProductVariant defaultVariant = new ProductVariant();
            defaultVariant.setName("常規"); // 或與商品同名
            defaultVariant.setPrice(request.getBasePrice());
            defaultVariant.setSkuCode(null);

            newProduct.addVariant(defaultVariant);
            newProduct.setBasePrice(request.getBasePrice());
        }

        // 4. 儲存 (Cascade 會一併儲存 Variants)
        Product savedProduct = productRepository.save(newProduct);

        return convertToDto(savedProduct);
    }

    /**
     * 連結選項群組，並清除快取
     */
    @Transactional
    @CacheEvict(value = {"product-summary", "product-pos"}, key = "#brandId") // 【新增】清除快取
    public Product linkOptionGroupsToProduct(Long brandId, Long productId, Set<Long> groupIds) {
        Product product = productRepository.findByBrand_IdAndId(brandId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + productId));

        Set<OptionGroup> groupsToLink = groupIds.stream()
                .map(groupId -> optionGroupRepository.findByBrand_IdAndId(brandId, groupId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "找不到選項群組，ID：" + groupId)))
                .collect(Collectors.toSet());

        product.setOptionGroups(groupsToLink);
        return productRepository.save(product);
    }

    /**
     * 取得商品摘要列表 (快取)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "product-summary", key = "#brandId") // 【新增】啟用快取
    public List<ProductSummaryDto> getAvailableSummaries(Long brandId) {
        List<Product> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 取得 POS 完整商品列表 (快取)
     * 這是 POS 載入時最重的查詢，快取後能大幅提升速度
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "product-pos", key = "#brandId") // 【新增】啟用快取
    public List<ProductPosDto> getAvailableProductsForPos(Long brandId) {
        List<Product> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductPosDto::fromEntity)
                .collect(Collectors.toList());
    }

    private ProductResponseDto convertToDto(Product product) {
        return ProductResponseDto.fromEntity(product);
    }
}