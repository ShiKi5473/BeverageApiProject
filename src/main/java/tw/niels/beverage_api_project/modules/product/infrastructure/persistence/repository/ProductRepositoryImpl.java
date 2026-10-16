package tw.niels.beverage_api_project.modules.product.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.product.domain.model.*;
import tw.niels.beverage_api_project.modules.product.domain.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.infrastructure.mapper.ProductMapper;
import tw.niels.beverage_api_project.modules.product.repository.CategoryJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Infrastructure - Repository Implementation
 * ============================================================
 *
 * Product BC Domain Repository 的 Infrastructure 層實現
 * 管理 Product、Category、OptionGroup 三個 Aggregate 的持久化
 *
 * 修改說明（P0-1）：
 * - 既有聚合：取同交易中的受管實體，把 Domain 狀態「套用」上去，由 dirty checking 寫回，
 *   不再 new 一個同 ID 的實體去 save()（會因 isNew()=true 走 persist → NonUniqueObjectException）。
 * - 新聚合：才 new 實體並 persist，之後以 assignId 回填 Domain ID（移除反射 setId）。
 * - 品牌關聯一律使用 EntityManager.getReference（不再 new BrandEntity + setId）。
 * - 分類／選項群組：只對「新加入」的 ID 做品牌隔離的批次查詢取得受管實體。
 *   不用 getReference 的原因：BaseTsidEntity 覆寫了 equals/hashCode，代理物件放入 HashSet 時
 *   呼叫 hashCode 仍會觸發初始化（一筆一個 SELECT），且無法驗證該 ID 屬於本品牌；
 *   批次查詢只需一個 SELECT，同時完成租戶隔離驗證。
 * - System.err 改用 logger。
 * ============================================================
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRepositoryImpl.class);

    private final ProductJpaRepository productJpaRepo;
    private final CategoryJpaRepository categoryJpaRepo;
    private final OptionGroupJpaRepository optionGroupJpaRepo;

    /** 新增：用於取得關聯參照（getReference），避免 new 出只有 ID 的關聯實體 */
    @PersistenceContext
    private EntityManager entityManager;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepo,
                                  CategoryJpaRepository categoryJpaRepo,
                                  OptionGroupJpaRepository optionGroupJpaRepo) {
        this.productJpaRepo = productJpaRepo;
        this.categoryJpaRepo = categoryJpaRepo;
        this.optionGroupJpaRepo = optionGroupJpaRepo;
    }

    // ─── Product 操作 ──────────────────────────────────────────────────────

    @Override
    public Optional<Product> findProductById(Long brandId, Long productId) {
        return productJpaRepo.findByBrand_IdAndId(brandId, productId)
            .map(ProductMapper::toDomain);
    }

    // 透過規格 ID 反查所屬商品，委派至 ProductJpaRepository 的 JPQL 查詢
    @Override
    public Optional<Product> findProductByVariantId(Long brandId, Long variantId) {
        return productJpaRepo.findByBrand_IdAndVariantId(brandId, variantId)
            .map(ProductMapper::toDomain);
    }

    @Override
    public List<Product> findActiveProductsByBrand(Long brandId) {
        return productJpaRepo.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE)
            .stream()
            .map(ProductMapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * 儲存商品聚合。
     * 修改（P0-1）：區分新建與更新；更新時操作受管實體，不再 save() 一個新的同 ID 實體。
     * 加上 @Transactional：若呼叫端沒有交易，也能確保「載入受管實體 → 套用 → flush」在同一交易內完成。
     */
    @Override
    @Transactional
    public void saveProduct(Product product) {
        Long brandId = product.getBrandId();

        if (product.getId() == null) {
            // ── 新商品：建立新實體並 persist ──
            ProductEntity entity = new ProductEntity();
            entity.setBrand(entityManager.getReference(BrandEntity.class, brandId));
            applyProduct(entity, product);
            productJpaRepo.save(entity); // 新實體 isNew()=true → persist，規格經 cascade 一併寫入
            product.assignId(entity.getId());
            log.debug("已建立商品 brandId={}, productId={}", brandId, entity.getId());
            return;
        }

        // ── 既有商品：取得受管實體（同交易中已載入時直接命中一級快取，不會產生第二個實例） ──
        ProductEntity managed = productJpaRepo.findByBrand_IdAndId(brandId, product.getId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + product.getId()));
        applyProduct(managed, product);
        // 不呼叫 save()：受管實體的變更由 Hibernate dirty checking 於 commit 時寫回
    }

    /** 新增：把 Domain 商品的所有狀態套用到實體（純量欄位、規格、分類、選項群組） */
    private void applyProduct(ProductEntity target, Product source) {
        ProductMapper.applyProductScalars(target, source);
        ProductMapper.syncVariants(target, source.getVariants());
        syncCategories(target, source.getBrandId(), source.getCategoryIds());
        syncOptionGroups(target, source.getBrandId(), source.getOptionGroupIds());
    }

    /**
     * 新增：依 ID 同步商品的分類集合。
     * 只移除不在清單中的、只批次載入新加入的，保留既有的受管實體（不整組替換，避免多餘的 DELETE/INSERT）。
     */
    private void syncCategories(ProductEntity target, Long brandId, List<Long> categoryIds) {
        Set<Long> desired = categoryIds != null ? new HashSet<>(categoryIds) : new HashSet<>();
        if (target.getCategories() == null) {
            target.setCategories(new HashSet<>());
        }

        // removeIf 只呼叫 getId()（識別子 getter 不會初始化代理）
        target.getCategories().removeIf(c -> !desired.contains(c.getId()));

        Set<Long> current = target.getCategories().stream()
            .map(CategoryEntity::getId)
            .collect(Collectors.toSet());
        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);

        if (!toAdd.isEmpty()) {
            Set<CategoryEntity> found = categoryJpaRepo.findByBrand_IdAndIdIn(brandId, toAdd);
            if (found.size() != toAdd.size()) {
                throw new BadRequestException("部分分類 ID 無效或不屬於此品牌");
            }
            target.getCategories().addAll(found);
        }
    }

    /** 新增：依 ID 同步商品的選項群組集合（邏輯同 syncCategories） */
    private void syncOptionGroups(ProductEntity target, Long brandId, List<Long> groupIds) {
        Set<Long> desired = groupIds != null ? new HashSet<>(groupIds) : new HashSet<>();
        if (target.getOptionGroups() == null) {
            target.setOptionGroups(new HashSet<>());
        }

        target.getOptionGroups().removeIf(g -> !desired.contains(g.getId()));

        Set<Long> current = target.getOptionGroups().stream()
            .map(OptionGroupEntity::getId)
            .collect(Collectors.toSet());
        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);

        if (!toAdd.isEmpty()) {
            List<OptionGroupEntity> found = optionGroupJpaRepo.findByBrand_IdAndIdIn(brandId, toAdd);
            if (found.size() != toAdd.size()) {
                throw new BadRequestException("部分選項群組 ID 無效或不屬於此品牌");
            }
            target.getOptionGroups().addAll(found);
        }
    }

    // ─── Category 操作 ────────────────────────────────────────────────────

    @Override
    public Optional<Category> findCategoryById(Long brandId, Long categoryId) {
        return categoryJpaRepo.findByBrand_IdAndId(brandId, categoryId)
            .map(ProductMapper::toCategoryDomain);
    }

    @Override
    public List<Category> findActiveCategoriesByBrand(Long brandId) {
        return categoryJpaRepo.findByBrand_IdAndIsActive(brandId, true)
            .stream()
            .map(ProductMapper::toCategoryDomain)
            .collect(Collectors.toList());
    }

    /**
     * 儲存分類。
     * 修改：新建時 new 實體並以 getReference 設定品牌；更新時套用到受管實體（同 saveProduct 的修正）。
     */
    @Override
    @Transactional
    public void saveCategory(Category category) {
        if (category.getId() == null) {
            CategoryEntity entity = new CategoryEntity();
            entity.setBrand(entityManager.getReference(BrandEntity.class, category.getBrandId()));
            ProductMapper.applyCategory(entity, category);
            categoryJpaRepo.save(entity);
            category.assignId(entity.getId());
            return;
        }

        CategoryEntity managed = categoryJpaRepo.findByBrand_IdAndId(category.getBrandId(), category.getId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到分類，ID：" + category.getId()));
        ProductMapper.applyCategory(managed, category);
    }

    @Override
    public boolean existsCategoryName(Long brandId, String name) {
        return name != null && categoryJpaRepo.findByBrand_IdAndNameIgnoreCase(brandId, name).isPresent();
    }

    @Override
    public Set<Long> findExistingCategoryIds(Long brandId, Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }
        return categoryJpaRepo.findByBrand_IdAndIdIn(brandId, new HashSet<>(categoryIds)).stream()
            .map(CategoryEntity::getId)
            .collect(Collectors.toSet());
    }

    // ─── OptionGroup 操作 ─────────────────────────────────────────────────

    @Override
    public Optional<OptionGroup> findOptionGroupById(Long brandId, Long groupId) {
        return optionGroupJpaRepo.findByBrand_IdAndId(brandId, groupId)
            .map(ProductMapper::toOptionGroupDomain);
    }

    @Override
    public List<OptionGroup> findOptionGroupsByBrand(Long brandId) {
        return optionGroupJpaRepo.findByBrand_Id(brandId)
            .stream()
            .map(ProductMapper::toOptionGroupDomain)
            .collect(Collectors.toList());
    }

    /**
     * 儲存選項群組。
     * 修改：新建時 new 實體（含選項，cascade 寫入）；更新時套用到受管實體並依 ID 同步選項。
     */
    @Override
    @Transactional
    public void saveOptionGroup(OptionGroup optionGroup) {
        if (optionGroup.getId() == null) {
            OptionGroupEntity entity = new OptionGroupEntity();
            entity.setBrand(entityManager.getReference(BrandEntity.class, optionGroup.getBrandId()));
            entity.setSortOrder(0); // Domain 沒有排序欄位，新建時給預設值
            ProductMapper.applyOptionGroup(entity, optionGroup);
            optionGroupJpaRepo.save(entity);
            optionGroup.assignId(entity.getId());
            return;
        }

        OptionGroupEntity managed = optionGroupJpaRepo.findByBrand_IdAndId(optionGroup.getBrandId(), optionGroup.getId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + optionGroup.getId()));
        ProductMapper.applyOptionGroup(managed, optionGroup);
    }

    @Override
    public Set<Long> findExistingOptionGroupIds(Long brandId, Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new HashSet<>();
        }
        return optionGroupJpaRepo.findByBrand_IdAndIdIn(brandId, groupIds).stream()
            .map(OptionGroupEntity::getId)
            .collect(Collectors.toSet());
    }
}
