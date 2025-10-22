package tw.niels.beverage_api_project.modules.user.dto;

import lombok.Data;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

/**
 * 用於回傳員工資訊的資料傳輸物件 (DTO)。
 * 這是根據最新的 User + StaffProfile 架構設計的。
 */
@Data
public class StaffDto {
    private Long userId; // 使用 User 的 ID
    private String primaryPhone; // 登入帳號，來自 User
    private String fullName; // 員工姓名，來自 StaffProfile
    private StaffRole role; // 員工角色，來自 StaffProfile
    private boolean isActive; // 帳號狀態，來自 User
    private Long storeId; // 店家 ID，來自 StaffProfile
    private String storeName; // 店家名稱，來自 StaffProfile
    private Long brandId; // 品牌 ID，來自 User

    /**
     * 靜態工廠方法，用於將 User 實體轉換為 StaffDto。
     * 這種設計確保了 DTO 的建立邏輯被封裝起來。
     *
     * @param user 包含員工資料的 User 實體
     * @return 轉換後的 StaffDto 物件
     */
    public static StaffDto fromEntity(User user) {
        // 基本的防呆處理，如果傳入的 user 是 null 或沒有 staffProfile，則不進行轉換
        if (user == null || user.getStaffProfile() == null) {
            return null;
        }

        StaffDto dto = new StaffDto();
        StaffProfile profile = user.getStaffProfile();

        // 從 User 實體中映射資料
        dto.setUserId(user.getUserId());
        dto.setPrimaryPhone(user.getPrimaryPhone());
        dto.setActive(user.getActive());
        if (user.getBrand() != null) {
            dto.setBrandId(user.getBrand().getBrandId());
        }

        // 從 StaffProfile 實體中映射資料
        dto.setFullName(profile.getFullName());
        dto.setRole(profile.getRole());

        // 處理可能為 null 的 Store 關聯 (例如品牌管理員沒有 store)
        if (profile.getStore() != null) {
            dto.setStoreId(profile.getStore().getStoreId());
            dto.setStoreName(profile.getStore().getName());
        }

        return dto;
    }
}