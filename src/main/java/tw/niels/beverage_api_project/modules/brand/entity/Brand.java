package tw.niels.beverage_api_project.modules.brand.entity;

import java.util.Date;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

@Entity
@Getter
@Setter
@Table(name = "brands")
@AttributeOverride(name = "id", column = @Column(name = "brand_id"))
public class Brand extends BaseTsidEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Date createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Date updatedAt;

}