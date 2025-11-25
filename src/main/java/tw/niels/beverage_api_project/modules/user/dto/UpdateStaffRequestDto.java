package tw.niels.beverage_api_project.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Data
@Schema(description = "更新員工資料請求")
public class UpdateStaffRequestDto {

    @Schema(description = "員工姓名", example = "王大明")
    private String fullName;

    @Schema(description = "職位角色", example = "MANAGER")
    private StaffRole role;

    @Schema(description = "所屬分店 ID (若為品牌管理員可為 null)", example = "1")
    private Long storeId;

    @Schema(description = "帳號是否啟用 (false 代表停權/離職)", example = "true")
    private Boolean isActive;
}