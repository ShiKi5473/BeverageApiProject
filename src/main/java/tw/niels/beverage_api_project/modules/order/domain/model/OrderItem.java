package tw.niels.beverage_api_project.modules.order.domain.model;

import tw.niels.beverage_api_project.common.domain.vo.Money;

import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;

import java.util.Collections;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Entity（屬於 Order Aggregate）
 * ============================================================
 *
 * 訂單品項實體（Domain Entity）
 *
 * 代表訂單內的單一商品項目，包含商品規格、數量、單價與客製選項。
 * OrderItem 屬於 Order Aggregate 的內部實體，生命週期由 Order 管理，
 * 不允許外部直接建立或修改（透過靜態工廠方法 {@link #create} 建立）。
 *
 * 業務規則：
 * - 數量必須大於 0
 * - 單價不得為負數（由 Money VO 保證）
 * - 小計 = 單價 × 數量（在建立時計算並固定）
 * - productSnapshot 為必填，確保歷史資料一致性
 *
 * 不變量（Invariants）：
 * - quantity > 0
 * - subtotal = unitPrice × quantity
 * ============================================================
 */
public class OrderItem {

    /** 訂單品項 ID（TSID），由 Application Layer / Infrastructure Layer 指派 */
    private Long id;

    /** 商品 ID，用於統計查詢（冗餘欄位，主要資料來自 productSnapshot） */
    private final Long productId;

    /** 商品規格 ID（如：大杯、中杯的具體 SKU） */
    private final Long variantId;

    /** 購買數量，必須大於 0 */
    private final int quantity;

    /** 單品售價（規格售價 + 所選選項加價，由 OrderItemFactory 計算） */
    private final Money unitPrice;

    /** 小計 = unitPrice × quantity（非 final：reconstitute() 需還原資料庫中的原值） */
    private Money subtotal;

    /** 顧客備註（如：少冰、半糖） */
    private final String notes;

    /**
     * 已選取的客製選項 ID 清單（如：加珍珠、去冰）
     * 以 ID 清單儲存，詳細資訊見 productSnapshot
     */
    private final List<Long> optionIds;

    /**
     * 商品快照：在下單時凍結商品資訊（名稱、分類、規格名稱、基礎售價），
     * 確保歷史訂單不受日後商品異動影響。
     */
    private final ProductSnapshot productSnapshot;

    /**
     * 私有建構子：強制使用靜態工廠方法建立。
     * 確保 subtotal 的計算邏輯集中在一處。
     */
    private OrderItem(Long productId, Long variantId, int quantity,
                      Money unitPrice, String notes, List<Long> optionIds,
                      ProductSnapshot productSnapshot) {
        this.productId = productId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        // 小計由建構子計算，外部無法單獨設定（保護 Invariant）
        this.subtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
        this.notes = notes;
        this.optionIds = optionIds != null ? List.copyOf(optionIds) : Collections.emptyList();
        this.productSnapshot = productSnapshot;
    }

    /**
     * 靜態工廠方法：建立一個訂單品項。
     *
     * 前置條件（Pre-condition）：
     * - quantity > 0
     * - unitPrice 不得為 null
     * - productSnapshot 不得為 null
     *
     * @param productId       商品 ID
     * @param variantId       規格 ID
     * @param quantity        數量（必須 > 0）
     * @param unitPrice       單品售價
     * @param notes           備註（可為 null）
     * @param optionIds       選項 ID 清單（可為空清單）
     * @param productSnapshot 商品快照（必填）
     * @return 新的 OrderItem 實例
     * @throws OrderDomainException 若 quantity ≤ 0 或必填參數為 null
     */
    public static OrderItem create(Long productId, Long variantId, int quantity,
                                   Money unitPrice, String notes, List<Long> optionIds,
                                   ProductSnapshot productSnapshot) {
        // 驗證業務規則
        if (quantity <= 0) {
            throw new OrderDomainException("訂單品項數量必須大於 0，收到：" + quantity);
        }
        if (unitPrice == null) {
            throw new OrderDomainException("訂單品項單價不得為 null");
        }
        if (productSnapshot == null) {
            throw new OrderDomainException("訂單品項的商品快照不得為 null");
        }
        return new OrderItem(productId, variantId, quantity, unitPrice, notes, optionIds, productSnapshot);
    }

    /**
     * 供 Infrastructure Mapper 從資料庫重建品項（不驗證不變量）。
     *
     * 新增方法：V5 之前建立的舊訂單沒有商品快照、早期資料也可能缺少規格，
     * 若沿用 create() 會因驗證失敗而讀不出訂單；重建時應忠實還原資料庫內容，
     * 因此不檢查 productSnapshot，並直接採用資料庫中的 subtotal（避免重新計算造成差異）。
     * 同時取代原本以反射呼叫 setId() 的作法。
     *
     * @param id              品項 ID
     * @param productId       商品 ID
     * @param variantId       規格 ID（舊資料可能為 null）
     * @param quantity        數量
     * @param unitPrice       單價（null 時視為 0）
     * @param subtotal        小計（null 時以單價 × 數量計算）
     * @param notes           備註
     * @param optionIds       選項 ID 清單
     * @param productSnapshot 商品快照（舊資料可能為 null）
     * @return 重建的 OrderItem
     */
    public static OrderItem reconstitute(Long id, Long productId, Long variantId, int quantity,
                                         Money unitPrice, Money subtotal, String notes,
                                         List<Long> optionIds, ProductSnapshot productSnapshot) {
        Money safeUnitPrice = unitPrice != null ? unitPrice : Money.ZERO;
        OrderItem item = new OrderItem(productId, variantId, quantity, safeUnitPrice, notes, optionIds, productSnapshot);
        item.id = id;
        if (subtotal != null) {
            item.subtotal = subtotal;
        }
        return item;
    }

    // ─── Getters（無 setters，確保不可變）─────────────────────────────────

    /** 訂單品項 ID（由 reconstitute() 自資料庫還原；新建品項在持久化前為 null） */
    public Long getId() { return id; }

    public Long getProductId() { return productId; }
    public Long getVariantId() { return variantId; }
    public int getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public Money getSubtotal() { return subtotal; }
    public String getNotes() { return notes; }

    /** 回傳不可修改的選項 ID 清單 */
    public List<Long> getOptionIds() { return optionIds; }

    public ProductSnapshot getProductSnapshot() { return productSnapshot; }
}
