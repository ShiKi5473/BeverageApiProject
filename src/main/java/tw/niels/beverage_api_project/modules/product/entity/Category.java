package tw.niels.beverage_api_project.modules.product.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;

@Entity
@Getter
@Setter
@Table(name = "categories", uniqueConstraints = { @UniqueConstraint(columnNames = { "brand_id", "name" }) })
@AttributeOverride(name = "id", column = @Column(name = "category_id"))
public class Category extends BaseTsidEntity {

    public Category() {
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToMany(mappedBy = "categories")
    private Set<Product> products = new HashSet<>();

}
