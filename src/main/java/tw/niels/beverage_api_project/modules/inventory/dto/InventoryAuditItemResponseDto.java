package tw.niels.beverage_api_project.modules.inventory.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "盤點清單項目 (回應)")
public record InventoryAuditItemResponseDto(
        // [P0-10] TSID 超過 JS Number.MAX_SAFE_INTEGER，回應中的 ID 一律序列化為字串，避免前端精度遺失
        @Schema(description = "原物料 ID (字串)")
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,

        @Schema(description = "品名")
        String name,

        @Schema(description = "單位")
        String unit,

        @Schema(description = "目前系統庫存 (本店批次總和)")
        BigDecimal quantity
) {}
