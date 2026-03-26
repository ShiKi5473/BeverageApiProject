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
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.dto.*;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;

@Service
public class ProductService {
    private final ProductJpaRepository productRepository;
    private final BrandJpaRepository brandRepository;
    private final CategoryJpaRepository CategoryJpaRepository;
    private final OptionGroupJpaRepository OptionGroupJpaRepository;
    private final ProductVariantJpaRepository ProductVariantJpaRepository;

    public ProductService(ProductJpaRepository productRepository,
                          BrandJpaRepository brandRepository,
                          CategoryJpaRepository CategoryJpaRepository,
                          OptionGroupJpaRepository OptionGroupJpaRepository,
                          ProductVariantJpaRepository ProductVariantJpaRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.CategoryJpaRepository = CategoryJpaRepository;
        this.OptionGroupJpaRepository = OptionGroupJpaRepository;
        this.ProductVariantJpaRepository = ProductVariantJpaRepository;
    }

    /**
     * 建立商品，並自動建立預設規格 (Default Variant)
     */
    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#brandId")
    public ProductResponseDto createProduct(Long brandId, CreateProductRequestDto request) {
        // 修正：使用 ResourceNotFoundException 取代 RuntimeException，使 GlobalExceptionHandler 回傳 HTTP 404
        BrandEntity BrandEntity = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        Set<CategoryEntity> categories = validateAndGetCategories(brandId, request.getCategoryIds());
        Set<OptionGroupEntity> optionGroups = validateAndGetOptionGroups(brandId, request.getOptionGroupIds());

        ProductEntity newProduct = buildProductEntity(BrandEntity, request, categories, optionGroups);

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            buildVariantsAndCalculateBasePrice(newProduct, request.getVariants());
        } else {
            buildDefaultVariantAndBasePrice(newProduct, request.getBasePrice());
        }

        ProductEntity savedProduct = productRepository.save(newProduct);
        return convertToDto(savedProduct);
    }

    private Set<CategoryEntity> validateAndGetCategories(Long brandId, Set<Long> categoryIds) {
        Set<CategoryEntity> categories = CategoryJpaRepository.findByBrand_IdAndIdIn(brandId, categoryIds);
        if (categories.size() != categoryIds.size()) {
            // 修正：使用 BadRequestException 取代 RuntimeException，使 GlobalExceptionHandler 回傳 HTTP 400
            throw new BadRequestException("部分分類 ID 無效");
        }
        return categories;
    }

    private Set<OptionGroupEntity> validateAndGetOptionGroups(Long brandId, Set<Long> optionGroupIds) {
        if (optionGroupIds == null || optionGroupIds.isEmpty()) {
            return new HashSet<>();
        }
        return optionGroupIds.stream()
                .map(groupId -> OptionGroupJpaRepository
                        .findByBrand_IdAndId(brandId, groupId)
                        .orElseThrow(() -> new BadRequestException(
                                "無效的選項群組 ID：" + groupId + " 或不屬於此品牌")))
                .collect(Collectors.toSet());
    }

    private ProductEntity buildProductEntity(BrandEntity BrandEntity, CreateProductRequestDto request, Set<CategoryEntity> categories, Set<OptionGroupEntity> optionGroups) {
        ProductEntity newProduct = new ProductEntity();
        newProduct.setBrand(BrandEntity);
        newProduct.setName(request.getName());
        newProduct.setDescription(request.getDescription());
        newProduct.setBasePrice(request.getBasePrice());
        newProduct.setImageUrl(request.getImageUrl());
        newProduct.setStatus(request.getStatus());
        newProduct.setCategories(categories);
        newProduct.setOptionGroups(optionGroups);
        return newProduct;
    }

    private void buildVariantsAndCalculateBasePrice(ProductEntity ProductEntity, List<CreateProductVariantDto> variantDtos) {
        BigDecimal minPrice = null;
        for (CreateProductVariantDto variantDto : variantDtos) {
            ProductVariantEntity variant = new ProductVariantEntity();
            variant.setName(variantDto.name());
            variant.setPrice(variantDto.price());
            variant.setSkuCode(variantDto.skuCode());
            ProductEntity.addVariant(variant);

            if (minPrice == null || variant.getPrice().compareTo(minPrice) < 0) {
                minPrice = variant.getPrice();
            }
        }
        ProductEntity.setBasePrice(minPrice);
    }

    private void buildDefaultVariantAndBasePrice(ProductEntity ProductEntity, BigDecimal basePrice) {
        if (basePrice == null) {
            throw new BadRequestException("若未指定規格，則必須填寫基本售價");
        }
        ProductVariantEntity defaultVariant = new ProductVariantEntity();
        defaultVariant.setName("常規");
        defaultVariant.setPrice(basePrice);
        defaultVariant.setSkuCode(null);
        ProductEntity.addVariant(defaultVariant);
        ProductEntity.setBasePrice(basePrice);
    }

    /**
     * 連結選項群組，並清除快取
     */
    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#brandId") // 【新增】清除快取
    public ProductEntity linkOptionGroupsToProduct(Long brandId, Long productId, Set<Long> groupIds) {
        ProductEntity ProductEntity = productRepository.findByBrand_IdAndId(brandId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + productId));

        Set<OptionGroupEntity> groupsToLink = groupIds.stream()
                .map(groupId -> OptionGroupJpaRepository.findByBrand_IdAndId(brandId, groupId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "找不到選項群組，ID：" + groupId)))
                .collect(Collectors.toSet());

        ProductEntity.setOptionGroups(groupsToLink);
        return productRepository.save(ProductEntity);
    }

    /**
     * 取得商品摘要列表 (快取)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "ProductEntity-summary", key = "#brandId") // 【新增】啟用快取
    public List<ProductSummaryDto> getAvailableSummaries(Long brandId) {
        List<ProductEntity> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 取得 POS 完整商品列表 (快取)
     * 這是 POS 載入時最重的查詢，快取後能大幅提升速度
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "ProductEntity-pos", key = "#brandId") // 【新增】啟用快取
    public List<ProductPosDto> getAvailableProductsForPos(Long brandId) {
        List<ProductEntity> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductPosDto::fromEntity)
                .collect(Collectors.toList());
    }

    private ProductResponseDto convertToDto(ProductEntity ProductEntity) {
        return ProductResponseDto.fromEntity(ProductEntity);
    }

    /**
     * 新增單一規格
     */
    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#brandId")
    public ProductResponseDto addProductVariant(Long brandId, Long productId, CreateProductVariantDto requestDto) {
        ProductEntity ProductEntity = productRepository.findByBrand_IdAndId(brandId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + productId));

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setName(requestDto.name());
        variant.setPrice(requestDto.price());
        variant.setSkuCode(requestDto.skuCode());
        variant.setDeleted(false); // 確保預設為未刪除

        // 建立關聯
        ProductEntity.addVariant(variant);

        // 更新商品 Base Price (若新規格價格更低)
        if (ProductEntity.getBasePrice() == null || variant.getPrice().compareTo(ProductEntity.getBasePrice()) < 0) {
            ProductEntity.setBasePrice(variant.getPrice());
        }

        productRepository.save(ProductEntity); // 會 Cascade save variant

        return ProductResponseDto.fromEntity(ProductEntity);
    }

    /**
     * 更新規格
     * 修改：更新後自動重新計算商品 BasePrice
     */
    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#brandId")
    public void updateProductVariant(Long brandId, Long variantId, UpdateProductVariantDto requestDto) {
        ProductVariantEntity variant = ProductVariantJpaRepository.findByProduct_Brand_IdAndIdAndIsDeletedFalse(brandId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到規格或已刪除，ID：" + variantId));

        boolean priceChanged = false;

        variant.setName(requestDto.name());

        if (requestDto.price() != null && requestDto.price().compareTo(variant.getPrice()) != 0) {
            variant.setPrice(requestDto.price());
            priceChanged = true;
        }

        variant.setSkuCode(requestDto.skuCode());

        ProductVariantJpaRepository.save(variant); // 先儲存變更

        // 若價格有變動，重新計算該商品的 BasePrice
        if (priceChanged) {
            recalculateProductBasePrice(variant.getProduct());
        }
    }

    /**
     * 軟刪除規格
     * 修改：刪除後自動重新計算商品 BasePrice
     */
    @Transactional
    @CacheEvict(value = {"ProductEntity-summary", "ProductEntity-pos"}, key = "#brandId")
    public void deleteProductVariant(Long brandId, Long variantId) {
        ProductVariantEntity variant = ProductVariantJpaRepository.findByProduct_Brand_IdAndIdAndIsDeletedFalse(brandId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到規格或已刪除，ID：" + variantId));

        // 檢查是否為該商品的最後一個有效規格
        long activeVariantsCount = ProductVariantJpaRepository
                .findByProduct_Brand_IdAndProduct_IdAndIsDeletedFalse(brandId, variant.getProduct().getId())
                .size();

        if (activeVariantsCount <= 1) {
            throw new BadRequestException("無法刪除：商品必須至少保留一個有效規格");
        }

        // 執行軟刪除
        variant.setDeleted(true);
        ProductVariantJpaRepository.save(variant); // 先儲存刪除狀態

        // 重新計算該商品的 BasePrice (因為刪除的可能是最低價規格)
        recalculateProductBasePrice(variant.getProduct());
    }

    // --- Private Helper Methods ---

    /**
     * 重新計算並更新商品的 BasePrice
     * 邏輯：找出所有「未刪除」的規格，取最低價更新回 ProductEntity
     */
    private void recalculateProductBasePrice(ProductEntity ProductEntity) {
        // 1. 查詢該商品目前所有的有效規格
        List<ProductVariantEntity> activeVariants = ProductVariantJpaRepository
                .findByProduct_Brand_IdAndProduct_IdAndIsDeletedFalse(ProductEntity.getBrand().getId(), ProductEntity.getId());

        if (activeVariants.isEmpty()) {
            return; // 理論上因刪除防護機制，不會走到這裡
        }

        // 2. 找出最低價格
        BigDecimal minPrice = activeVariants.stream()
                .map(ProductVariantEntity::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(ProductEntity.getBasePrice());

        // 3. 如果計算出的最低價與目前 BasePrice 不同，則更新並儲存
        if (ProductEntity.getBasePrice().compareTo(minPrice) != 0) {
            ProductEntity.setBasePrice(minPrice);
            productRepository.save(ProductEntity);
        }
    }
}