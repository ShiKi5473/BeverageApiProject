package tw.niels.beverage_api_project.modules.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "盤點清單項目 (回應)")
public record InventoryAuditItemResponseDto(
        @Schema(description = "原物料 ID")
        Long id,

        @Schema(description = "品名")
        String name,

        @Schema(description = "單位")
        String unit,

        @Schema(description = "目前系統庫存 (Snapshot)")
        BigDecimal quantity
) {}