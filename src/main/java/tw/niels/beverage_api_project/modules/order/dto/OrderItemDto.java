package tw.niels.beverage_api_project.modules.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "訂單品項明細")
public record OrderItemDto(

        @NotNull(message = "商品ID不可為空")
        @Schema(description = "商品 ID", example = "101")
        Long productId,

        @NotNull(message = "規格ID不可為空")
        @Schema(description = "規格 ID (對應 ProductVariant)", example = "102")
        Long variantId,

        @NotNull(message = "商品數量不可為空")
        @Min(value = 1, message = "數量至少為1")
        @Schema(description = "購買數量", example = "2")
        Integer quantity,

        @Schema(description = "品項備註 (如: 幫我寫生日快樂)", example = "少冰")
        String notes,

        @Schema(description = "客製化選項 ID 列表 (如: 半糖、去冰的 ID)", example = "[1, 5]")
        List<Long> optionIds
) {
}