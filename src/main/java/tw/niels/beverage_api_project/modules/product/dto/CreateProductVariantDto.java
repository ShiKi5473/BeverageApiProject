package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductVariantDto(
        @NotBlank(message = "規格名稱不可為空")
        @Schema(description = "規格名稱", example = "大杯")
        String name,

        @NotNull(message = "規格價格不可為空")
        @DecimalMin(value = "0.0", inclusive = false)
        @Schema(description = "規格價格", example = "60.00")
        BigDecimal price,

        @Schema(description = "SKU 代碼 (選填)", example = "TEA-L")
        String skuCode
) {}