package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 訂單金額試算請求 DTO（新增，B-8 前後端金額不一致）。
 *
 * 前端結帳頁的小計、折扣、應付金額改以後端試算結果為準，
 * 取代前端寫死的點數比率與 basePrice 計算。
 * - 員工：storeId 以 token 綁定的分店為準（品牌管理員可由 body 指定），memberId / pointsToUse 由 body 指定
 * - 會員：storeId 由 body 指定，memberId 固定為本人
 * - 訪客：storeId 由 body 指定，不可使用點數
 */
@Data
@Schema(description = "訂單金額試算請求")
public class OrderPreviewRequestDto {

    @Schema(description = "分店 ID（訪客 / 會員必填於線上點餐情境，可傳字串或數字）", example = "1")
    private Long storeId;

    @NotEmpty(message = "訂單品項不可為空")
    @Schema(description = "購買品項列表")
    private List<@Valid OrderItemDto> items;

    @Schema(description = "會員 ID（員工代會員試算時使用）", example = "5")
    private Long memberId;

    @Min(value = 0, message = "使用點數不可為負數")
    @Schema(description = "欲使用的點數", example = "10")
    private Long pointsToUse = 0L;
}
