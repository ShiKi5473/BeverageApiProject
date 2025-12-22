package tw.niels.beverage_api_project.modules.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.promotion.enums.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private PromotionType type;

    // 折扣數值 (金額或折數)
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

    // 適用商品 (多對多)
    // 對應 V1 SQL 中的 promotion_products 表
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promotion_products",
            joinColumns = @JoinColumn(name = "promotion_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> applicableProducts = new HashSet<>();

}