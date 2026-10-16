package tw.niels.beverage_api_project.modules.promotion.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促銷計算的輸入（與 JPA Entity／Order Aggregate 解耦）。
 * <p>
 * 由 Order BC 依訂單品項建立後傳給 PromotionService，
 * 讓促銷計算不再依賴 OrderEntity，可同時用於結帳與金額試算。
 * </p>
 *
 * @param brandId     品牌 ID
 * @param lines       訂單品項（每行一個品項）
 * @param totalAmount 折扣前訂單總額
 */
public record PromotionContext(Long brandId, List<Line> lines, BigDecimal totalAmount) {

    /**
     * 單一品項行。
     *
     * @param productId 商品 ID
     * @param quantity  數量
     * @param unitPrice 單價（含規格與選項加價）
     * @param subtotal  小計 = unitPrice × quantity
     */
    public record Line(Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
