package tw.niels.beverage_api_project.modules.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class LoginRequestDto {
    @NotEmpty
    private String username; // 對應到 User 的 primaryPhone

    @NotEmpty
    private String password;

    @NotNull
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