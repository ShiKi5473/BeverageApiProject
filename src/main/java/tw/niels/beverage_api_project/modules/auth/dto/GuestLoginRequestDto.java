package tw.niels.beverage_api_project.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "訪客登入請求資訊")
public record GuestLoginRequestDto(
        @NotEmpty
        @Schema(description = "訪客顯示名稱", example = "Mike")
        String displayName
) {}