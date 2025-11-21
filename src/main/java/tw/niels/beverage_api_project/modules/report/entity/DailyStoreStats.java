package tw.niels.beverage_api_project.modules.report.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "daily_store_stats", indexes = {
        @Index(name = "idx_daily_store_date", columnList = "store_id, date", unique = true), // 確保同一天同一分店只有一筆
        @Index(name = "idx_daily_brand_date", columnList = "brand_id, date")
})
public class DailyStoreStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private BigDecimal totalRevenue = BigDecimal.ZERO; // 總金額 (Gross Sales)

    @Column(name = "total_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscount = BigDecimal.ZERO; // 折扣金額

    @Column(name = "final_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalRevenue = BigDecimal.ZERO; // 實收金額 (Net Sales)

    @Column(name = "cancelled_orders", nullable = false)
    private Integer cancelledOrders = 0;

    // --- 支付方式細分 (可根據需求擴充) ---

    @Column(name = "cash_total", precision = 12, scale = 2)
    private BigDecimal cashTotal = BigDecimal.ZERO;

    @Column(name = "line_pay_total", precision = 12, scale = 2)
    private BigDecimal linePayTotal = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public DailyStoreStats() {}

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Integer getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Integer totalOrders) { this.totalOrders = totalOrders; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; }

    public BigDecimal getFinalRevenue() { return finalRevenue; }
    public void setFinalRevenue(BigDecimal finalRevenue) { this.finalRevenue = finalRevenue; }

    public Integer getCancelledOrders() { return cancelledOrders; }
    public void setCancelledOrders(Integer cancelledOrders) { this.cancelledOrders = cancelledOrders; }

    public BigDecimal getCashTotal() { return cashTotal; }
    public void setCashTotal(BigDecimal cashTotal) { this.cashTotal = cashTotal; }

    public BigDecimal getLinePayTotal() { return linePayTotal; }
    public void setLinePayTotal(BigDecimal linePayTotal) { this.linePayTotal = linePayTotal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}