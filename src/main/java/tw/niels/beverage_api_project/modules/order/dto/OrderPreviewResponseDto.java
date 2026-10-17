package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 訂單金額試算結果 DTO（新增）。
 *
 * 計算方式與 POS 結帳完全相同（共用 OrderCheckoutService）：
 * finalAmount = totalAmount - promotionDiscount - pointsDiscount（最低為 0）。
 *
 * @param totalAmount       原價總額（規格價 + 選項加價）× 數量的加總
 * @param promotionDiscount 最佳促銷折扣
 * @param pointsDiscount    點數折抵金額（上限為扣除促銷後的金額）
 * @param pointsUsed        實際會使用的點數（超出折抵上限的點數不會扣）
 * @param finalAmount       應付金額
 */
@Schema(description = "訂單金額試算結果")
public record OrderPreviewResponseDto(
        @Schema(description = "原價總額", example = "150.00") BigDecimal totalAmount,
        @Schema(description = "促銷折扣", example = "10.00") BigDecimal promotionDiscount,
        @Schema(description = "點數折抵金額", example = "5.00") BigDecimal pointsDiscount,
        @Schema(description = "實際使用點數", example = "50") Long pointsUsed,
        @Schema(description = "應付金額", example = "135.00") BigDecimal finalAmount
) {
}
