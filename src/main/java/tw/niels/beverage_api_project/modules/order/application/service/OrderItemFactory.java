package tw.niels.beverage_api_project.modules.order.application.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.repository.OrderProductLookupJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Application - Service（共用元件）
 * ============================================================
 *
 * 訂單品項工廠：把前端送來的 OrderItemDto 轉成 Domain OrderItem。
 *
 * 新增元件（P0-2）：原本 PlaceOrderHandler、PosCheckoutHandler、UpdateHeldOrderHandler 各自實作
 * processItems()，且都有錯：逐項載入整個商品聚合（N+1）、單價不含選項加價、快照寫入假資料
 * （"Snapshot Name"／"CategoryEntity"／"Category"）、備註沒有 Jsoup 清洗。
 * 現在統一由此元件處理，並沿用舊 OrderItemProcessorService 的正確邏輯：
 * - 依品牌批次查詢商品、規格、選項（多租戶隔離、避免 N+1）
 * - 規格必須屬於該商品；選項必須屬於該商品的選項群組
 * - 單價 = 規格售價 + 所選選項加價總和
 * - 備註以 Jsoup 清洗（防 XSS）
 * - 商品快照使用真實資料（商品名稱、分類名稱、規格售價、規格名稱）
 *
 * 使用者：PlaceOrderHandler、PosCheckoutHandler、UpdateHeldOrderHandler、
 *        PlaceOnlineOrderHandler、OrderPreviewHandler。
 * ============================================================
 */
@Component
public class OrderItemFactory {

    /** 商品沒有分類時快照使用的分類名稱 */
    private static final String UNCATEGORIZED = "未分類";

    private final OrderProductLookupJpaRepository productLookupRepository;
    private final ProductVariantJpaRepository productVariantJpaRepository;
    private final ProductOptionJpaRepository productOptionJpaRepository;

    public OrderItemFactory(OrderProductLookupJpaRepository productLookupRepository,
                            ProductVariantJpaRepository productVariantJpaRepository,
                            ProductOptionJpaRepository productOptionJpaRepository) {
        this.productLookupRepository = productLookupRepository;
        this.productVariantJpaRepository = productVariantJpaRepository;
        this.productOptionJpaRepository = productOptionJpaRepository;
    }

    /**
     * 將品項請求轉為 Domain OrderItem 清單（需在交易內呼叫）。
     *
     * @param brandId  品牌 ID（多租戶隔離）
     * @param itemDtos 前端送來的品項
     * @return Domain OrderItem 清單（順序與請求相同）
     * @throws BadRequestException       品項為空、缺少必要欄位、規格／選項不屬於該商品
     * @throws ResourceNotFoundException 商品不存在或已下架
     */
    public List<OrderItem> createItems(Long brandId, List<OrderItemDto> itemDtos) {
        if (itemDtos == null || itemDtos.isEmpty()) {
            throw new BadRequestException("訂單品項不可為空");
        }

        // 1. 收集 ID（同時檢查必要欄位：MQ 消費者路徑不會經過 Bean Validation）
        Set<Long> productIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>();
        Set<Long> allOptionIds = new HashSet<>();
        for (OrderItemDto dto : itemDtos) {
            if (dto == null || dto.productId() == null) {
                throw new BadRequestException("商品ID不可為空");
            }
            if (dto.variantId() == null) {
                throw new BadRequestException("必須選擇商品規格 (Variant ID)");
            }
            if (dto.quantity() == null || dto.quantity() < 1) {
                throw new BadRequestException("數量至少為1");
            }
            productIds.add(dto.productId());
            variantIds.add(dto.variantId());
            if (dto.optionIds() != null) {
                dto.optionIds().stream().filter(Objects::nonNull).forEach(allOptionIds::add);
            }
        }

        // 2. 批次查詢商品（含分類與選項群組）
        Map<Long, ProductEntity> productMap = productLookupRepository
                .findForOrderByBrandIdAndIdIn(brandId, productIds)
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity(), (a, b) -> a));

        // 3. 批次查詢規格（僅未刪除者）
        Map<Long, ProductVariantEntity> variantMap = productVariantJpaRepository
                .findByProduct_Brand_IdAndIdInAndIsDeletedFalse(brandId, variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity(), (a, b) -> a));

        // 4. 批次查詢選項（JOIN FETCH 選項群組）
        Map<Long, ProductOptionEntity> optionMap = new HashMap<>();
        if (!allOptionIds.isEmpty()) {
            productOptionJpaRepository.findByOptionGroup_Brand_IdAndIdIn(brandId, allOptionIds)
                    .forEach(opt -> optionMap.put(opt.getId(), opt));
        }

        // 5. 逐項組裝 Domain OrderItem
        List<OrderItem> items = new ArrayList<>(itemDtos.size());
        for (OrderItemDto dto : itemDtos) {
            items.add(buildItem(dto, productMap, variantMap, optionMap));
        }
        return items;
    }

    /**
     * 以 Jsoup 清除所有 HTML 標籤（防 XSS），並去除前後空白；空字串回傳 null。
     * 供品項備註與訂單 customerNote 共用。
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = Jsoup.clean(text, Safelist.none()).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // ─── 私有輔助方法 ─────────────────────────────────────────────────────

    private OrderItem buildItem(OrderItemDto dto,
                                Map<Long, ProductEntity> productMap,
                                Map<Long, ProductVariantEntity> variantMap,
                                Map<Long, ProductOptionEntity> optionMap) {
        // 商品必須存在於本品牌，且未下架（status 為 null 的舊資料視為可售）
        ProductEntity product = productMap.get(dto.productId());
        if (product == null || (product.getStatus() != null && product.getStatus() != ProductStatus.ACTIVE)) {
            throw new ResourceNotFoundException("商品不存在或已下架，ID: " + dto.productId());
        }

        // 規格必須存在且屬於該商品（variant.getProduct().getId() 不會觸發代理初始化）
        ProductVariantEntity variant = variantMap.get(dto.variantId());
        if (variant == null) {
            throw new BadRequestException("規格無效或已刪除，ID: " + dto.variantId());
        }
        if (variant.getProduct() == null || !Objects.equals(variant.getProduct().getId(), product.getId())) {
            throw new BadRequestException("規格 ID 與商品 ID 不匹配");
        }

        // 選項：去除重複，且必須屬於該商品綁定的選項群組
        Set<Long> allowedGroupIds = product.getOptionGroups().stream()
                .map(OptionGroupEntity::getId)
                .collect(Collectors.toSet());
        Set<Long> selectedOptionIds = dto.optionIds() == null ? Set.of()
                : dto.optionIds().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));

        BigDecimal optionsPrice = BigDecimal.ZERO;
        for (Long optionId : selectedOptionIds) {
            ProductOptionEntity option = optionMap.get(optionId);
            if (option == null) {
                throw new BadRequestException("無效選項: " + optionId);
            }
            Long groupId = option.getOptionGroup() != null ? option.getOptionGroup().getId() : null;
            if (groupId == null || !allowedGroupIds.contains(groupId)) {
                throw new BadRequestException("選項 " + optionId + " 不屬於商品 " + product.getId() + " 的選項群組");
            }
            if (option.getPriceAdjustment() != null) {
                optionsPrice = optionsPrice.add(option.getPriceAdjustment());
            }
        }

        // 單價 = 規格售價 + 選項加價（不得為負）
        BigDecimal variantPrice = variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;
        BigDecimal unitPrice = variantPrice.add(optionsPrice).max(BigDecimal.ZERO);

        // 真實商品快照（取代原本的假資料）
        ProductSnapshot snapshot = new ProductSnapshot(
                product.getId(),
                product.getName(),
                Money.of(variantPrice.max(BigDecimal.ZERO)),
                resolveCategoryName(product),
                variant.getName()
        );

        return OrderItem.create(
                product.getId(),
                variant.getId(),
                dto.quantity(),
                Money.of(unitPrice),
                sanitize(dto.notes()),
                new ArrayList<>(selectedOptionIds),
                snapshot
        );
    }

    /**
     * 取商品的「第一個」分類名稱；為了結果穩定，依 sortOrder、名稱排序後取第一個。
     */
    private static String resolveCategoryName(ProductEntity product) {
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            return UNCATEGORIZED;
        }
        return product.getCategories().stream()
                .sorted(Comparator
                        .comparing((CategoryEntity c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                        .thenComparing(c -> c.getName() == null ? "" : c.getName()))
                .map(CategoryEntity::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(UNCATEGORIZED);
    }
}
