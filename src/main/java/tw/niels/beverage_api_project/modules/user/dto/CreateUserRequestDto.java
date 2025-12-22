package tw.niels.beverage_api_project.modules.user.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.modules.user.enums.Gender;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Getter
@Setter
@Schema(description = "建立使用者請求 (包含員工或會員資料)")
public class CreateUserRequestDto {
    @NotNull
    @Schema(description = "品牌 ID", example = "1")
    private Long brandId;

    @NotEmpty
    @Schema(description = "登入帳號 (手機號碼)", example = "0987654321")
    private String primaryPhone;

    @NotEmpty
    @Schema(description = "密碼", example = "password123")
    private String password;

    @Schema(description = "員工資料 (若為會員則留空)")
    private StaffProfileDto staffProfile;

    @Schema(description = "會員資料 (若為員工則留空)")
    private MemberProfileDto memberProfile;

    // Nested DTO for Staff Profile
    @Getter
    @Setter
    @Schema(description = "員工詳細資料")
    public static class StaffProfileDto {
        @Schema(description = "所屬分店 ID", example = "1")
        private Long storeId;
        @NotEmpty
        @Schema(description = "姓名", example = "王小明")
        private String fullName;
        @Schema(description = "員工編號", example = "EMP001")
        private String employeeNumber;
        @NotNull
        @Schema(description = "職位角色", example = "STAFF")
        private StaffRole role;
        private Date hireDate;


    }

    // Nested DTO for Member Profile
    @Getter
    @Setter
    @Schema(description = "會員詳細資料")
    public static class MemberProfileDto {
        @Schema(description = "姓名", example = "陳大戶")
        private String fullName;
        @Schema(description = "Email", example = "member@example.com")
        private String email;
        private Date birthDate;
        private Gender gender;
        private String notes;

    }

}