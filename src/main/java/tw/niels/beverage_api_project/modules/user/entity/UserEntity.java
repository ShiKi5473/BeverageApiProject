package tw.niels.beverage_api_project.modules.user.entity;

import java.util.Date;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfileEntity;

@Entity
@Getter
@Setter
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class UserEntity extends BaseTsidEntity {

    public UserEntity() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandEntity brand;

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

    // 雙向關聯到 StaffProfileEntity
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private StaffProfileEntity staffProfile;

    // 雙向關聯到 MemberProfileEntity
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MemberProfileEntity memberProfile;

    // Getters and Setters
    public Long getUserId() {
        return getId();
    }

    public void setUserId(Long userId) {
        setId(userId);
    }


    public void setStaffProfile(StaffProfileEntity staffProfile) {
        this.staffProfile = staffProfile;
        if (staffProfile != null) {
            staffProfile.setUser(this);
        }
    }

    public void setMemberProfile(MemberProfileEntity memberProfile) {
        this.memberProfile = memberProfile;
        if (memberProfile != null) {
            memberProfile.setUser(this);
        }
    }
}