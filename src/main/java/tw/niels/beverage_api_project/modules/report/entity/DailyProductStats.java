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
@Table(name = "daily_product_stats", indexes = {
        @Index(name = "idx_prod_store_date", columnList = "store_id, date"),
        @Index(name = "idx_prod_brand_date", columnList = "brand_id, date")
})
@AttributeOverride(name = "id", column = @Column(name = "daily_product_stats_id"))
public class DailyProductStats extends BaseTsidEntity {


    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "quantity_sold", nullable = false)
    private Integer quantitySold = 0;

    @Column(name = "total_sales_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSalesAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public DailyProductStats() {}

    // --- Getters and Setters ---

    // 語義化的 ID Getter/Setter
    public Long getDailyProductStatsId() {
        return getId();
    }

    public void setDailyProductStatsId(Long id) {
        setId(id);
    }

}