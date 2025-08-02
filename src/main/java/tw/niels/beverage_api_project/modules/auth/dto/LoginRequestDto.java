package tw.niels.beverage_api_project.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class LoginRequestDto {
    @NotBlank(message = "品牌ID不可為空")
    private Long brandId;

    @NotBlank(message = "帳號不可為空")
    private String username;

    @NotBlank(message = "密碼不可為空")
    private String password;
}
