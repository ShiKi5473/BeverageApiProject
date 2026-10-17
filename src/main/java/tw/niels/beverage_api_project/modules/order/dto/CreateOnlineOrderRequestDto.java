package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 建立線上訂單請求 DTO（新增）。
 *
 * 新增原因（B-4）：原本線上下單共用 CreateOrderRequestDto，初始狀態由前端傳入
 * （可直接送 CLOSED / PREPARING 跳過接單，送 'RECEIVED' 則 500），且會員沒有 storeId 會 400。
 * 現在：
 * - 不接受 status（初始狀態固定為 AWAITING_ACCEPTANCE，舊前端多送的 status 欄位會被忽略）；
 * - storeId 由請求帶入（訪客 / 會員沒有綁定分店），後端驗證分店屬於該品牌。
 */
@Schema(description = "建立線上訂單請求")
@Getter
@Setter
public class CreateOnlineOrderRequestDto {

    @NotNull(message = "分店ID不可為空")
    @Schema(description = "下單分店 ID（可傳字串或數字）", example = "1")
    private Long storeId;

    @NotEmpty(message = "訂單品項不可為空")
    @Schema(description = "購買品項列表")
    private List<@Valid OrderItemDto> items;

    @Size(max = 255, message = "顧客備註最多 255 字")
    @Schema(description = "顧客備註", example = "請幫我多拿吸管")
    private String customerNote;

    public CreateOnlineOrderRequestDto() {
    }
}
