package tw.niels.beverage_api_project.modules.user.entity;

import java.util.Date;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;

@Entity
@Getter
@Setter
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class User extends BaseTsidEntity {

    public User() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "primary_phone")
    private String primaryPhone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Date createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Date updatedAt;

    // 雙向關聯到 StaffProfile
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private StaffProfile staffProfile;

    // 雙向關聯到 MemberProfile
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MemberProfile memberProfile;

    // Getters and Setters
    public Long getUserId() {
        return getId();
    }

    public void setUserId(Long userId) {
        setId(userId);
    }


    public void setStaffProfile(StaffProfile staffProfile) {
        this.staffProfile = staffProfile;
        if (staffProfile != null) {
            staffProfile.setUser(this);
        }
    }

    public void setMemberProfile(MemberProfile memberProfile) {
        this.memberProfile = memberProfile;
        if (memberProfile != null) {
            memberProfile.setUser(this);
        }
    }
}