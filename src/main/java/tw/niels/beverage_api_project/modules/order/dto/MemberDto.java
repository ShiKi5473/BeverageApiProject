package tw.niels.beverage_api_project.modules.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;

@Data
@Schema(description = "會員基本資訊 (用於 POS 查詢)")
public class MemberDto {
    // 修改（P0-10）：會員 ID 為 TSID，超過 JS 安全整數範圍，改以字串輸出避免前端失真
    @Schema(description = "會員 ID（字串）")
    @JsonSerialize(using = ToStringSerializer.class)
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

    // 修改：參數名稱由批次改名殘留的 "UserEntity" 改回 user（避免與型別同名）
    public static MemberDto fromEntity(UserEntity user) {
        if (user == null || user.getMemberProfile() == null) return null;
        MemberProfileEntity profile = user.getMemberProfile();
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
