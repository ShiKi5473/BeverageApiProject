package tw.niels.beverage_api_project.modules.brand.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@Table(name = "brand_point_configs")
public class BrandPointConfig {

    @Id
    @Column(name = "brand_id")
    private Long brandId;

    // 使用 @MapsId 與 @OneToOne 共享主鍵，這樣 config 的 ID 就是 brand_id
    @OneToOne
    @MapsId
    @JoinColumn(name = "brand_id")
    private Brand brand;

    // 累積匯率：每消費多少元獲得 1 點 (預設 1.0 元)
    @Column(name = "earn_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal earnRate = BigDecimal.ONE;

    // 折抵匯率：每 1 點可折抵多少元 (預設 0.1 元，即 10 點折 1 元)
    @Column(name = "redeem_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal redeemRate = new BigDecimal("0.1");

    // (可選) 點數過期天數，null 代表永不過期
    @Column(name = "expiry_days")
    private Integer expiryDays;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


    public BrandPointConfig() {}

    public BrandPointConfig(Brand brand) {
        this.brand = brand;
    }


}
