package tw.niels.beverage_api_project.modules.promotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Schema(description = "建立促銷活動請求")
public class CreatePromotionRequestDto {

    @NotBlank
    @Schema(description = "活動名稱", example = "開幕慶全館九折")
    private String name;

    @Schema(description = "活動描述", example = "歡慶開幕，限時優惠")
    private String description;

    @NotNull
    @Schema(description = "促銷類型", example = "PERCENTAGE")
    private PromotionType type;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "折扣數值 (金額或折數，如 0.9)", example = "0.9")
    private BigDecimal value;

    @DecimalMin(value = "0.0")
    @Schema(description = "最低消費門檻 (null 為無門檻)", example = "100.00")
    private BigDecimal minSpend;

    @NotNull
    @Schema(description = "開始時間")
    private LocalDateTime startDate;

    @NotNull
    @Future(message = "結束時間必須在未來")
    @Schema(description = "結束時間")
    private LocalDateTime endDate;

    @Schema(description = "適用商品 ID 列表 (空列表代表適用全館商品)", example = "[101, 102]")
    private Set<Long> applicableProductIds;
}