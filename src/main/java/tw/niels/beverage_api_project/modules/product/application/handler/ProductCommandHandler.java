package tw.niels.beverage_api_project.modules.product.application.handler;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.domain.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.application.ProductCacheNames;
import tw.niels.beverage_api_project.modules.product.application.command.*;
import tw.niels.beverage_api_project.modules.product.domain.model.Category;
import tw.niels.beverage_api_project.modules.product.domain.model.Product;
import tw.niels.beverage_api_project.modules.product.domain.model.ProductVariant;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Application - Command Handler
 * ============================================================
 *
 * 商品相關命令處理器（Phase 3 重構完成版）
 *
 * 處理商品、規格、分類、選項群組的建立與修改。
 * 全部操作透過 {@link ProductRepository}（Domain 介面）操作 Domain Model，
 * 不再委派 ProductService，符合 DDD Application Layer 職責。
 *
 * 處理流程（以 CreateProduct 為例）：
 * 1. 驗證品牌存在（BrandRepository）
 * 2. 驗證商品名稱不重複（ProductJpaRepository，過渡期保留）
 * 3. 建立 Product Aggregate + ProductVariant 子實體
 * 4. 透過 ProductRepository 持久化
 * 5. 清除商品快取（product-summary、product-pos，見 {@link ProductCacheNames}）
 * 6. 回傳新建商品 ID
 * ============================================================
 */
@Component
public class ProductCommandHandler {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    /** 僅用於商品名稱重複性檢查，過渡期保留直至 ProductRepository 補齊此查詢 */
    private final ProductJpaRepository productJpaRepo;

    public ProductCommandHandler(ProductRepository productRepository,
                                 BrandRepository brandRepository,
                                 ProductJpaRepository productJpaRepo) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.productJpaRepo = productJpaRepo;
    }

    // ─── 商品操作 ──────────────────────────────────────────────────────────

    /**
     * 建立新商品。
     *
     * 處理流程：
     * 1. 驗證品牌存在
     * 2. 驗證商品名稱在品牌內唯一
     * 3. 建立 Product Domain Model 並加入規格（無規格時建立預設「常規」規格）
     * 4. 指定分類與選項群組，重算基礎售價後儲存
     *
     * @param command 包含品牌 ID 與建立商品所需資料
     * @return 新建商品的 ID
     */
    @Transactional
    @CacheEvict(value = {ProductCacheNames.SUMMARY, ProductCacheNames.POS}, key = "#command.brandId()") // 快取名稱改回 product-*（常數）
    public Long handle(CreateProductCommand command) {
        var req = command.requestDto();
        Long brandId = command.brandId();

        // 1. 驗證品牌存在
        brandRepository.findById(brandId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 驗證商品名稱在品牌內唯一
        if (productJpaRepo.findByBrand_IdAndName(brandId, req.getName()).isPresent()) {
            throw new BadRequestException("商品名稱已存在：" + req.getName());
        }

        // 3. 建立 Product Aggregate（空白規格清單，後續逐一加入）
        boolean isActive = req.getStatus() == ProductStatus.ACTIVE;
        Product product = new Product(
            null, brandId,
            req.getName(), req.getDescription(),
            Money.of(req.getBasePrice()), req.getImageUrl(),
            isActive,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        );

        // 4a. 加入規格（有則逐一建立，無則建立預設「常規」規格）
        if (req.getVariants() != null && !req.getVariants().isEmpty()) {
            for (var variantDto : req.getVariants()) {
                product.addVariant(ProductVariant.create(
                    variantDto.name(), Money.of(variantDto.price()), variantDto.skuCode()
                ));
            }
        } else {
            // 無規格時以基本售價建立預設規格，避免商品無法計算 basePrice
            if (req.getBasePrice() == null) {
                throw new BadRequestException("若未指定規格，則必須填寫基本售價");
            }
            product.addVariant(ProductVariant.create("常規", Money.of(req.getBasePrice()), null));
        }

        // 4b. 指定分類與選項群組
        // 新增：先以品牌隔離的批次查詢驗證 ID（舊 ProductService 有此檢查，DDD 改寫時遺漏），
        //       避免把其他品牌的分類／群組掛到本品牌商品上
        validateCategoryIds(brandId, req.getCategoryIds());
        validateOptionGroupIds(brandId, req.getOptionGroupIds());
        if (req.getCategoryIds() != null) {
            req.getCategoryIds().forEach(product::assignCategory);
        }
        if (req.getOptionGroupIds() != null) {
            req.getOptionGroupIds().forEach(product::linkOptionGroup);
        }

        // 4c. 依規格重算基礎售價（確保 basePrice = 所有有效規格中最低價）
        product.recalculateBasePrice();

        productRepository.saveProduct(product);
        return product.getId();
    }

    /**
     * 新增商品規格。
     *
     * 處理流程：
     * 1. 載入商品 Domain Model
     * 2. 檢查規格名稱不重複
     * 3. 建立新規格並加入，重算 basePrice 後儲存
     *
     * @param command 包含品牌 ID、商品 ID 與規格資料
     * @return 所屬商品的 ID
     */
    @Transactional
    @CacheEvict(value = {ProductCacheNames.SUMMARY, ProductCacheNames.POS}, key = "#command.brandId()") // 快取名稱改回 product-*（常數）
    public Long handle(AddProductVariantCommand command) {
        Long brandId = command.brandId();
        Long productId = command.productId();
        var req = command.requestDto();

        // 1. 載入商品
        Product product = productRepository.findProductById(brandId, productId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + productId));

        // 2. 記憶體內檢查規格名稱不重複（只檢查有效規格）
        boolean nameExists = product.getVariants().stream()
            .anyMatch(v -> v.isActive() && v.getName().equals(req.name()));
        if (nameExists) {
            throw new BadRequestException("規格名稱已存在：" + req.name());
        }

        // 3. 建立並加入新規格，重算 basePrice
        product.addVariant(ProductVariant.create(req.name(), Money.of(req.price()), req.skuCode()));
        product.recalculateBasePrice();

        productRepository.saveProduct(product);
        return product.getId();
    }

    /**
     * 更新商品規格（名稱、售價、SKU 代碼）。
     *
     * 處理流程：
     * 1. 透過規格 ID 反查所屬商品
     * 2. 找到目標規格並呼叫 update()
     * 3. 重算 basePrice 後儲存
     *
     * @param command 包含品牌 ID、規格 ID 與更新資料
     */
    @Transactional
    @CacheEvict(value = {ProductCacheNames.SUMMARY, ProductCacheNames.POS}, key = "#command.brandId()") // 快取名稱改回 product-*（常數）
    public void handle(UpdateProductVariantCommand command) {
        Long brandId = command.brandId();
        Long variantId = command.variantId();
        var req = command.requestDto();

        // 1. 透過規格 ID 反查所屬商品
        Product product = productRepository.findProductByVariantId(brandId, variantId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到規格或已刪除，ID：" + variantId));

        // 2. 找到目標規格並更新
        ProductVariant target = product.getVariants().stream()
            .filter(v -> v.getId().equals(variantId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("規格不在商品內，ID：" + variantId));

        Money newPrice = req.price() != null ? Money.of(req.price()) : null;
        target.update(req.name(), newPrice, req.skuCode());

        // 3. 重算 basePrice 後儲存（價格異動時維護不變量）
        product.recalculateBasePrice();
        productRepository.saveProduct(product);
    }

    /**
     * 軟刪除商品規格。
     *
     * 業務規則：商品必須保留至少一個有效規格，否則拒絕刪除。
     *
     * 處理流程：
     * 1. 透過規格 ID 反查所屬商品
     * 2. 驗證有效規格數量 > 1
     * 3. 停用目標規格（isActive = false → Mapper 轉換為 isDeleted = true）
     * 4. 重算 basePrice 後儲存
     *
     * @param command 包含品牌 ID 與規格 ID
     */
    @Transactional
    @CacheEvict(value = {ProductCacheNames.SUMMARY, ProductCacheNames.POS}, key = "#command.brandId()") // 快取名稱改回 product-*（常數）
    public void handle(DeleteProductVariantCommand command) {
        Long brandId = command.brandId();
        Long variantId = command.variantId();

        // 1. 透過規格 ID 反查所屬商品
        Product product = productRepository.findProductByVariantId(brandId, variantId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到規格或已刪除，ID：" + variantId));

        // 2. 驗證有效規格數量 > 1
        long activeCount = product.getVariants().stream().filter(ProductVariant::isActive).count();
        if (activeCount <= 1) {
            throw new BadRequestException("無法刪除：商品必須至少保留一個有效規格");
        }

        // 3. 停用目標規格
        ProductVariant target = product.getVariants().stream()
            .filter(v -> v.getId().equals(variantId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("規格不在商品內，ID：" + variantId));
        target.deactivate();

        // 4. 重算 basePrice（刪除的可能是最低價規格）後儲存
        product.recalculateBasePrice();
        productRepository.saveProduct(product);
    }

    /**
     * 連結（替換）商品的選項群組。
     *
     * 完整替換語意：清除原有連結後以新清單重建。
     * 每個 groupId 在寫入前驗證其存在於此品牌下。
     *
     * @param command 包含品牌 ID、商品 ID 與新的選項群組 ID 集合
     */
    @Transactional
    @CacheEvict(value = {ProductCacheNames.SUMMARY, ProductCacheNames.POS}, key = "#command.brandId()") // 快取名稱改回 product-*（常數）
    public void handle(LinkOptionGroupsCommand command) {
        Long brandId = command.brandId();
        Long productId = command.productId();

        // 1. 載入商品
        Product product = productRepository.findProductById(brandId, productId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + productId));

        // 2. 驗證每個 groupId 在此品牌下存在
        // 修改：原本逐筆 findOptionGroupById（每筆都載入整個群組含選項，N+1），改為一次批次查詢
        if (command.groupIds() != null && !command.groupIds().isEmpty()) {
            Set<Long> existing = productRepository.findExistingOptionGroupIds(brandId, command.groupIds());
            List<Long> missing = command.groupIds().stream()
                .filter(id -> !existing.contains(id))
                .toList();
            if (!missing.isEmpty()) {
                throw new ResourceNotFoundException("找不到選項群組，ID：" + missing);
            }
        }

        // 3. 完整替換選項群組連結並儲存
        product.replaceOptionGroups(
            command.groupIds() != null ? new ArrayList<>(command.groupIds()) : new ArrayList<>()
        );
        productRepository.saveProduct(product);
    }

    // ─── 分類操作 ──────────────────────────────────────────────────────────

    /**
     * 建立商品分類。
     *
     * @param command 包含品牌 ID 與分類資料
     * @return 新建分類的 ID
     */
    @Transactional
    public Long handle(CreateCategoryCommand command) {
        Long brandId = command.brandId();

        // 1. 驗證品牌存在
        brandRepository.findById(brandId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 新增：同品牌分類名稱不可重複（DB 有 UNIQUE(brand_id, name)，先檢查以回傳 400 而非 DB 例外）
        if (productRepository.existsCategoryName(brandId, command.requestDto().getName())) {
            throw new BadRequestException("分類名稱已存在：" + command.requestDto().getName());
        }

        // 2. 建立 Category Domain Model 並儲存
        int sortOrder = command.requestDto().getSortOrder() != null
            ? command.requestDto().getSortOrder() : 0;
        Category category = new Category(null, brandId, command.requestDto().getName(), sortOrder, true);

        productRepository.saveCategory(category);
        return category.getId();
    }

    // ─── 私有驗證方法 ──────────────────────────────────────────────────────

    /** 新增：驗證分類 ID 全部屬於此品牌（批次查詢一次） */
    private void validateCategoryIds(Long brandId, Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        Set<Long> existing = productRepository.findExistingCategoryIds(brandId, categoryIds);
        List<Long> invalid = categoryIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new BadRequestException("以下分類 ID 無效或不屬於此品牌：" + invalid);
        }
    }

    /** 新增：驗證選項群組 ID 全部屬於此品牌（批次查詢一次） */
    private void validateOptionGroupIds(Long brandId, Set<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        Set<Long> existing = productRepository.findExistingOptionGroupIds(brandId, groupIds);
        List<Long> invalid = groupIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new BadRequestException("以下選項群組 ID 無效或不屬於此品牌：" + invalid);
        }
    }
}
