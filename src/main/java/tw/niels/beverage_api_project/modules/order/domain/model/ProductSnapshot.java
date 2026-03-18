package tw.niels.beverage_api_project.modules.order.domain.model;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 商品快照值物件（Value Object）
 *
 * 在訂單建立時，將商品的關鍵資訊（名稱、價格、分類、規格）複製一份快照
 * 儲存於訂單品項中，確保歷史訂單資料不受商品異動（改名、下架、調價）影響。
 *
 * 為什麼需要快照？
 * 若訂單品項直接關聯 Product，當商品調整售價後，
 * 歷史訂單的明細會顯示「新售價」，導致財務資料不一致。
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final，無 setter。
 *
 * 注意：此類別為 Domain Layer 版本，與 `modules/order/vo/ProductSnapshot.java`
 * 共存，待 Phase 3 完成後再整併。
 * ============================================================
 */
public record ProductSnapshot(
        /** 商品 ID（TSID），用於統計查詢 */
        Long productId,

        /** 商品於下單當下的名稱 */
        String name,

        /** 商品於下單當下的基本售價（未計算選項加價） */
        Money basePrice,

        /** 商品於下單當下所屬的分類名稱 */
        String categoryName,

        /** 商品規格名稱（如：大杯、中杯） */
        String variantName
) {}
