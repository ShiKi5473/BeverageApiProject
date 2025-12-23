package tw.niels.beverage_api_project.modules.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "手動盤點請求 (庫存修正)")
public class InventoryAuditRequestDto {

    @Schema(description = "盤點備註", example = "月底例行盤點")
    private String note;

    @NotEmpty(message = "盤點項目不可為空")
    @Schema(description = "盤點項目列表")
    private List<@Valid AuditItemDto> items;

    @Data
    public static class AuditItemDto {
        @NotNull
        @Schema(description = "原物料 ID", example = "101")
        private Long inventoryItemId;

        @NotNull
        @Min(0)
        @Schema(description = "實際盤點數量 (絕對值)", example = "50.0")
        private BigDecimal actualQuantity;

        @Schema(description = "單項備註 (選填)", example = "破損報廢 2 個")
        private String itemNote;

        // 僅在盤盈時使用，允許為 null
        @Schema(description = "盤盈商品的效期 (選填)，若不填則由系統推斷")
        private LocalDate gainedItemExpiryDate;
    }
}