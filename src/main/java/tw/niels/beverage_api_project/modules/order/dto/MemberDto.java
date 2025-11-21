package tw.niels.beverage_api_project.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;

@Data
@Schema(description = "會員基本資訊 (用於 POS 查詢)")
public class MemberDto {
    @Schema(description = "會員 ID")
    private Long userId;
    @Schema(description = "手機號碼")
    private String primaryPhone;
    @Schema(description = "姓名")
    private String fullName;
    @Schema(description = "Email")
    private String email;
    @Schema(description = "目前持有總點數")
    private Long totalPoints;
    @Schema(description = "備註")
    private String notes;

    public static MemberDto fromEntity(User user) {
        if (user == null || user.getMemberProfile() == null) return null;
        MemberProfile profile = user.getMemberProfile();
        MemberDto dto = new MemberDto();
        dto.setUserId(profile.getUserId());
        dto.setPrimaryPhone(profile.getUser().getPrimaryPhone());
        dto.setFullName(profile.getFullName());
        dto.setEmail(profile.getEmail());
        dto.setTotalPoints(profile.getTotalPoints());
        dto.setNotes(profile.getNotes());
        return dto;
    }
}
