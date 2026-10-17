package tw.niels.beverage_api_project.modules.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfileEntity;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

/**
 * 用於回傳員工資訊的資料傳輸物件 (DTO)。
 * 這是根據最新的 UserEntity + StaffProfile 架構設計的。
 */
@Data
@Schema(description = "員工資訊回應")
public class StaffDto {

    @Schema(description = "使用者 ID", example = "10")
    // 所有 ID 欄位以字串輸出，避免 TSID 超過 JS 安全整數範圍而失真(P0-10)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "登入帳號 (手機)", example = "0912345678")
    private String primaryPhone;

    @Schema(description = "員工姓名", example = "王小明")
    private String fullName;

    @Schema(description = "員工職位", example = "STAFF")
    private StaffRole role;

    @Schema(description = "帳號是否啟用", example = "true")
    private boolean isActive;

    @Schema(description = "所屬分店 ID", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storeId;

    @Schema(description = "所屬分店名稱", example = "台北信義店")
    private String storeName;

    @Schema(description = "所屬品牌 ID", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long brandId;

    /**
     * 靜態工廠方法，用於將 UserEntity 實體轉換為 StaffDto。
     * 這種設計確保了 DTO 的建立邏輯被封裝起來。
     *
     * @param user 包含員工資料的 UserEntity 實體
     * @return 轉換後的 StaffDto 物件
     */
    // 清理(P3)：參數原本與型別同名（UserEntity UserEntity），改為 user
    public static StaffDto fromEntity(UserEntity user) {
        // 基本的防呆處理，如果傳入的 user 是 null 或沒有 staffProfile，則不進行轉換
        if (user == null || user.getStaffProfile() == null) {
            return null;
        }

        StaffDto dto = new StaffDto();
        StaffProfileEntity profile = user.getStaffProfile();

        // 從 UserEntity 實體中映射資料
        dto.setUserId(user.getUserId());
        dto.setPrimaryPhone(user.getPrimaryPhone());
        dto.setActive(user.getIsActive());
        if (user.getBrand() != null) {
            dto.setBrandId(user.getBrand().getId());
        }

        // 從 StaffProfile 實體中映射資料
        dto.setFullName(profile.getFullName());
        dto.setRole(profile.getRole());

        // 處理可能為 null 的 StoreEntity 關聯 (例如品牌管理員沒有 StoreEntity)
        if (profile.getStore() != null) {
            dto.setStoreId(profile.getStore().getId());
            dto.setStoreName(profile.getStore().getName());
        }

        return dto;
    }
}