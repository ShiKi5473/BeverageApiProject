package tw.niels.beverage_api_project.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "使用者登入請求資訊")
public class LoginRequestDto {
    @NotEmpty
    @Schema(description = "使用者帳號 (手機號碼)", example = "0912345678")
    private String username; // 對應到 User 的 primaryPhone

    @NotEmpty
    @Schema(description = "密碼", example = "password123")
    private String password;

    @NotNull
    @Schema(description = "品牌 ID", example = "1")
    private Long brandId; // 新增品牌 ID

}