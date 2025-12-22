package tw.niels.beverage_api_project.modules.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "platform_admins")
@AttributeOverride(name = "id", column = @Column(name = "admin_id"))
public class PlatformAdmin extends BaseTsidEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Date createdAt;

    // Getters and Setters
    public Long getAdminId() {
        return getId(); }
    public void setAdminId(Long adminId) {
        setId(adminId);
    }

}