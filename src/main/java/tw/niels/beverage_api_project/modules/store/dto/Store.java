package tw.niels.beverage_api_project.modules.store.dto;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="stores")
public class Store {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="store_id")
    private Long storeId;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name= "brand_id", nullable=false)
    private Long brandId;

    @Column(name="name", nullable=false, length=100)
    private String name;

    @Column(name="address", length=255)
    private String address;

    @Column(name="phone_number", length=20)
    private String phoneNumber;

    @CreatedDate
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;

}
