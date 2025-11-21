package tw.niels.beverage_api_project.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }
}