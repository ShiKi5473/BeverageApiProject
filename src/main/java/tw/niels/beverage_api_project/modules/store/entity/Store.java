package tw.niels.beverage_api_project.modules.store.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Getter
@Setter
@Table(name = "stores")
@AttributeOverride(name = "id", column = @Column(name = "store_id"))
public class Store extends BaseTsidEntity {

    public Store() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone", length = 20)
    private String phoneNumber;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}
