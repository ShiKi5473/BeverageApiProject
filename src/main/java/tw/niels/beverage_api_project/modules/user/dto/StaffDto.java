package tw.niels.beverage_api_project.modules.user.dto;

import lombok.Data;
import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Data
public class StaffDto {
        private Long staffId;
    private String username;
    private String fullName;
    private StaffRole role;
    private boolean isActive;
    private Long storeId;
    private String storeName;
    private Long brandId;

    public static StaffDto fromEntity(Staff staff){
        if (staff == null) {
            return null;
        }

        StaffDto dto = new StaffDto();
        dto.setStaffId(staff.getStaffId());
        dto.setUsername(staff.getUsername());
        dto.setFullName(staff.getFullName());
        dto.setRole(staff.getRole());
        dto.setActive(staff.isActive());

        if (staff.getStore() != null) {
            dto.setStoreId(staff.getStore().getStoreId());
            dto.setStoreName(staff.getStore().getName());
        }

        if (staff.getBrand() != null) {
            dto.setBrandId(staff.getBrand().getBrandId());
        }

        return dto;
    }
}

