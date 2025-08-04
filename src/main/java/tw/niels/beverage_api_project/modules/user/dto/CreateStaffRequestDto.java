package tw.niels.beverage_api_project.modules.user.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Data
public class CreateStaffRequestDto {

    private Long storeId;

    @NotBlank(message = "帳號不可為空")
    @Size(min = 3, max = 50, message = "帳號長度必須介於 3 到 50 之間")
    private String userName;
    @NotBlank(message = "密碼不可為空")
    @Size(min = 6, message = "密碼長度至少需要 6 個字元")
    private String password;
    private String fullName;
    @NotNull(message = "必須指定角色")
    private StaffRole role;
}
