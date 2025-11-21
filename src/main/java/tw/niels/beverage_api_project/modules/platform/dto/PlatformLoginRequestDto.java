package tw.niels.beverage_api_project.modules.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "平台管理員登入請求")
public class PlatformLoginRequestDto {

    @NotEmpty
    @Schema(description = "管理員帳號", example = "admin")
    private String username;

    @NotEmpty
    @Schema(description = "管理員密碼", example = "admin123")
    private String password;

}