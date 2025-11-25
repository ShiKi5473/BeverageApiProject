package tw.niels.beverage_api_project.modules.promotion.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@AttributeOverride(name = "id", column = @Column(name = "promotion_id"))
public class Promotion extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    // 對應到 promotion_types 表的 type_code，這裡用 Enum 對應
    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private PromotionType type;

    // 折扣數值 (若是 FIXED_AMOUNT 則為金額，若是 PERCENTAGE 則為折數如 0.9)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    // 最低消費門檻 (null 代表無門檻)
    @Column(name = "min_spend", precision = 10, scale = 2)
    private BigDecimal minSpend;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Getters & Setters
    public Long getPromotionId() { return getId(); }
    public void setPromotionId(Long id) { setId(id); }

    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public BigDecimal getMinSpend() { return minSpend; }
    public void setMinSpend(BigDecimal minSpend) { this.minSpend = minSpend; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public Boolean getActive() { return isActive; }
    public void setActive(Boolean active) { isActive = active; }
}