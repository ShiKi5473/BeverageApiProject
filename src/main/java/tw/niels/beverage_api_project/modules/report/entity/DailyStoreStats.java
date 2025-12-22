package tw.niels.beverage_api_project.modules.report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "daily_store_stats", indexes = {
        @Index(name = "idx_daily_store_date", columnList = "store_id, date", unique = true),
        @Index(name = "idx_daily_brand_date", columnList = "brand_id, date")
})
@AttributeOverride(name = "id", column = @Column(name = "daily_store_stats_id"))
public class DailyStoreStats extends BaseTsidEntity {


    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // --- 核心指標 ---

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "final_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalRevenue = BigDecimal.ZERO;

    @Column(name = "cancelled_orders", nullable = false)
    private Integer cancelledOrders = 0;

    // --- 支付方式細分 ---

    @Column(name = "cash_total", precision = 12, scale = 2)
    private BigDecimal cashTotal = BigDecimal.ZERO;

    @Column(name = "line_pay_total", precision = 12, scale = 2)
    private BigDecimal linePayTotal = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public DailyStoreStats() {}

    // --- Getters and Setters ---

    // 語義化的 ID Getter/Setter
    public Long getDailyStoreStatsId() {
        return getId();
    }

    public void setDailyStoreStatsId(Long id) {
        setId(id);
    }

}