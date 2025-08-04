package tw.niels.beverage_api_project.modules.brand.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBrandRequestDto {
    @NotBlank(message = "品牌名稱不可為空")
    @Size(max = 100)
    private String brandName;

    @NotNull(message = "必須提供管理員資訊")
    @Valid // 啟用對巢狀物件的驗證
    private BrandAdminDto admin;

    @Data
    public static class BrandAdminDto {
        @NotBlank(message = "管理員帳號不可為空")
        @Size(min = 3, max = 50)
        private String username;

        @NotBlank(message = "管理員密碼不可為空")
        @Size(min = 6)
        private String password;

        @NotBlank(message = "管理員姓名不可為空")
        private String fullName;
    }
}
