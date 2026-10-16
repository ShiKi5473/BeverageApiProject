package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;

/**
 * 更新暫存訂單請求 DTO
 *
 * 用於 PUT /{orderId}（更新 PENDING / HELD 訂單品項）。
 * 與 {@link CreateOrderRequestDto} 的語意不同：
 * - 建立訂單（Create）：初始化新訂單，指定起始狀態
 * - 更新訂單（Update）：完整替換品項；可選擇保留或切換 PENDING ↔ HELD 狀態
 *
 * status 允許的值：
 * - {@code PENDING}：訂單仍待處理（等待送出廚房）
 * - {@code HELD}：訂單暫存中（顧客稍後繼續點餐）
 */
@Schema(description = "更新暫存訂單請求")
@Getter
@Setter
public class UpdateOrderRequestDto {

    @NotNull(message = "訂單狀態不可為空")
    @Schema(description = "目標狀態（僅允許 PENDING 或 HELD）", example = "HELD")
    private OrderStatus status;

    @NotEmpty(message = "訂單品項不可為空")
    @Schema(description = "替換後的品項列表（完整替換，非增量更新）")
    private List<@Valid OrderItemDto> items;

    public UpdateOrderRequestDto() {
    }
}
