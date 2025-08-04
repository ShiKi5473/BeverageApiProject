package tw.niels.beverage_api_project.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class LoginRequestDto {
    @NotNull(message = "品牌ID不可為空")
    private Long brandId;

    @NotBlank(message = "帳號不可為空")
    private String userName;

    @NotBlank(message = "密碼不可為空")
    private String password;
}
