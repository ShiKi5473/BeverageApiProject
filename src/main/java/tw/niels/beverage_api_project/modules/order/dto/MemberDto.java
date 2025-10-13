package tw.niels.beverage_api_project.modules.order.dto;

import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;

public class MemberDto {
    private Long userId;
    private String primaryPhone;
    private String fullName;
    private String email;
    private Long totalPoints;
    private String notes;

    public static MemberDto fromEntity(User user) {
        if (user == null || user.getMemberProfile() == null) {
            return null;
        }
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

    // getter and setter
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public void setPrimaryPhone(String primaryPhone) {
        this.primaryPhone = primaryPhone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Long totalPoints) {
        this.totalPoints = totalPoints;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

}
