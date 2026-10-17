package tw.niels.beverage_api_project.modules.order.application.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
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
import java.util.regex.Pattern;
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
 * - 備註以 Jsoup 移除 HTML 標籤後存成純文字（不跳脫實體，m-3 修改）
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

    // ─── 備註純文字化（m-3 新增）────────────────────────────────────────────
    /** 標籤清除最多重複次數（處理多層實體編碼，例如 {@code "&amp;lt;b&amp;gt;"}） */
    private static final int MAX_SANITIZE_PASSES = 3;
    /** 視為換行的 HTML 標籤：br、br/、/p、/div、/li（不分大小寫） */
    private static final Pattern HTML_LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>|</(?:p|div|li)\\s*>");
    /** 控制字元（保留換行與 Tab） */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    /** 行尾空白（半形空白、Tab、全形空白 U+3000） */
    private static final Pattern TRAILING_LINE_SPACES = Pattern.compile("[ \\t\\x{3000}]+\\n");
    /** 連續 3 個以上換行（壓成一個空行） */
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

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
     * 將備註轉為「純文字」：清除所有 HTML 標籤，但不做 HTML 實體跳脫；保留換行、去除前後空白；空字串回傳 null。
     * 供品項備註與訂單 customerNote 共用（PlaceOnlineOrderHandler 也會呼叫，簽章不可變）。
     *
     * 修改（m-3）：原本 {@code Jsoup.clean(text, Safelist.none())} 輸出的是「HTML」，會把實體跳脫並重新排版
     * （{@code "少冰 & 半糖"} 存成 {@code "少冰 &amp; 半糖"}、換行被壓掉），前端改以純文字顯示後會直接看到 {@code &amp;}。
     * 現在：
     * 1. 統一換行符號（\r\n、\r → \n），並把 &lt;br&gt;、&lt;/p&gt;、&lt;/div&gt;、&lt;/li&gt; 視為換行
     * 2. Jsoup 解析後以 Safelist.none() 的 Cleaner 移除所有標籤（&lt;script&gt; 等內容一併丟棄），
     *    再以 wholeText() 取回「已解碼、保留原始空白與換行」的純文字（不會產生 {@code &amp;}）
     * 3. 重複步驟 2 直到結果不再改變（最多 {@value #MAX_SANITIZE_PASSES} 次）：例如使用者輸入 {@code "&lt;b&gt;"}
     *    解碼後變成 {@code "<b>"} 會再被清除；同時確保 sanitize(sanitize(x)) == sanitize(x)，暫存單往返更新時備註不會一直變動
     * 4. 移除控制字元（保留換行與 Tab；PostgreSQL text 欄位不接受 NUL 字元）、去除每行行尾空白、
     *    連續 3 個以上換行壓成一個空行，最後以 strip() 去除前後空白（含全形空白）
     * 原本沒有長度限制（DB 欄位為 text；customerNote 由 DTO @Size(max = 255) 限制），維持不變。
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        // 1. 統一換行符號，並把常見的換行標籤轉成 \n（避免 "a<br>b" 被清成 "ab"）
        String plain = text.replace("\r\n", "\n").replace('\r', '\n');
        plain = HTML_LINE_BREAK.matcher(plain).replaceAll("\n");

        // 2~3. 移除標籤並取回純文字，重複到結果穩定為止
        for (int i = 0; i < MAX_SANITIZE_PASSES; i++) {
            String next = stripHtmlTags(plain);
            if (next.equals(plain)) {
                break;
            }
            plain = next;
        }

        // 4. 移除控制字元、行尾空白、過多空行，最後去除前後空白
        plain = CONTROL_CHARS.matcher(plain).replaceAll("");
        plain = TRAILING_LINE_SPACES.matcher(plain).replaceAll("\n");
        plain = EXCESS_BLANK_LINES.matcher(plain).replaceAll("\n\n");
        plain = plain.strip();
        return plain.isEmpty() ? null : plain;
    }

    /**
     * 新增（m-3）：以 Jsoup 移除所有 HTML 標籤，回傳「未跳脫」的純文字（保留原始空白與換行）。
     * Cleaner + Safelist.none() 只保留文字節點：一般標籤（如 &lt;b&gt;）保留內文，
     * &lt;script&gt; / &lt;style&gt; 的內容與 HTML 註解則整段丟棄。
     */
    private static String stripHtmlTags(String text) {
        Document dirty = Jsoup.parseBodyFragment(text);
        Document clean = new Cleaner(Safelist.none()).clean(dirty);
        return clean.body().wholeText();
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
