package tw.niels.beverage_api_project.modules.user.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name= "staff", uniqueConstraints={
    @UniqueConstraint(columnNames={"brand_id", "username"})
})
public class Staff {
    @Id
    @Column(name="staff_id")
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long StaffId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="brand_id", nullable=false)
    private Brand brand;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="store_id", nullable=true) //總店人員此格為null
    private Store store;

    @Column(name="username", nullable=false, length=50)
    private String username;

    @Column(name="password_hash", nullable=false, length=255)
    private String passwordHash;

    @Column(name="full_name", length=100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name="role", nullable=false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private StaffRole role;

    @Column(name="is_active", nullable=false)
    private boolean isActive;
}
