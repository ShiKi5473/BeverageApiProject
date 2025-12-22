package tw.niels.beverage_api_project.modules.user.entity.profile;

import java.util.Date;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.enums.Gender;

@Entity
@Getter
@Setter
@Table(name = "member_profiles")
public class MemberProfile {

    public MemberProfile() {
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "birth_date")
    private Date birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Gender gender;

    @Column(name = "total_points", nullable = false)
    private Long totalPoints = 0L;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Version
    private Integer version;
}
