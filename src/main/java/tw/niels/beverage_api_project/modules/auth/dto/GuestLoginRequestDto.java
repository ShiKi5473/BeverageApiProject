package tw.niels.beverage_api_project.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "訪客登入請求資訊")
public record GuestLoginRequestDto(
        @NotEmpty
        @Size(max = 50) // 【新增】限制暱稱長度，避免超長字串寫入 token 與推播內容
        @Schema(description = "訪客顯示名稱", example = "Mike")
        String displayName,

        // 【新增】訪客必須指定品牌（例如從店內 QR code 的 URL 參數取得），
        // 寫入 guest token 供線上點餐依品牌隔離商品與分店。可傳字串或數字（Jackson 自動轉為 Long）。
        @NotNull
        @Schema(description = "品牌 ID", example = "1")
        Long brandId
) {}
