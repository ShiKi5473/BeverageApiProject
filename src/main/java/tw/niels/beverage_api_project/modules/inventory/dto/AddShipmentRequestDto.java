package tw.niels.beverage_api_project.modules.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "進貨請求資料")
public class AddShipmentRequestDto {

    @Schema(description = "供應商名稱", example = "光泉")
    private String supplier;

    @Schema(description = "廠商單據/發票號碼", example = "INV-20231225-001")
    private String invoiceNo;

    @Schema(description = "備註", example = "進貨 10 箱")
    private String notes;

    @NotEmpty(message = "進貨品項不可為空")
    @Schema(description = "進貨批次列表")
    private List<@Valid BatchItemDto> items;

    @Data
    @Schema(description = "單一進貨批次明細")
    public static class BatchItemDto {

        @NotNull(message = "原物料 ID 不可為空")
        @Schema(description = "原物料 ID", example = "5")
        private Long inventoryItemId;

        @NotNull
        @DecimalMin(value = "0.01", message = "數量必須大於 0")
        @Schema(description = "進貨數量", example = "100.00")
        private BigDecimal quantity;

        @NotNull(message = "有效期限不可為空")
        @Schema(description = "有效期限", example = "2023-12-31")
        private LocalDate expiryDate;
    }
}