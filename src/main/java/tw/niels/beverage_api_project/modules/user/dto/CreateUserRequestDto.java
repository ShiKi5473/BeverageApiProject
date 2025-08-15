package tw.niels.beverage_api_project.modules.user.dto;

import java.util.Date;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.user.enums.Gender;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

public class CreateUserRequestDto {

    @NotNull
    private Long brandId;

    @NotEmpty
    private String primaryPhone;

    @NotEmpty
    private String password;

    private StaffProfileDto staffProfile;

    private MemberProfileDto memberProfile;

    // Nested DTO for Staff Profile
    public static class StaffProfileDto {
        private Long storeId;
        @NotEmpty
        private String fullName;
        private String employeeNumber;
        @NotNull
        private StaffRole role;
        private Date hireDate;

        // Getters and Setters
        public Long getStoreId() {
            return storeId;
        }

        public void setStoreId(Long storeId) {
            this.storeId = storeId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmployeeNumber() {
            return employeeNumber;
        }

        public void setEmployeeNumber(String employeeNumber) {
            this.employeeNumber = employeeNumber;
        }

        public StaffRole getRole() {
            return role;
        }

        public void setRole(StaffRole role) {
            this.role = role;
        }

        public Date getHireDate() {
            return hireDate;
        }

        public void setHireDate(Date hireDate) {
            this.hireDate = hireDate;
        }
    }

    // Nested DTO for Member Profile
    public static class MemberProfileDto {
        private String fullName;
        private String email;
        // 【修改】新增其他會員欄位
        private Date birthDate;
        private Gender gender;
        private String notes;

        // Getters and Setters
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

        public Date getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(Date birthDate) {
            this.birthDate = birthDate;
        }

        public Gender getGender() {
            return gender;
        }

        public void setGender(Gender gender) {
            this.gender = gender;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    // Getters and Setters for main DTO
    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public void setPrimaryPhone(String primaryPhone) {
        this.primaryPhone = primaryPhone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public StaffProfileDto getStaffProfile() {
        return staffProfile;
    }

    public void setStaffProfile(StaffProfileDto staffProfile) {
        this.staffProfile = staffProfile;
    }

    public MemberProfileDto getMemberProfile() {
        return memberProfile;
    }

    public void setMemberProfile(MemberProfileDto memberProfile) {
        this.memberProfile = memberProfile;
    }
}