package tw.niels.beverage_api_project.modules.product.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="categories", uniqueConstraints={ @UniqueConstraint(columnNames={"brand_id", "name"})})
public class Category {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="category_id")
    private Long categoryId;

    @ManyToMany(fetch=FetchType.LAZY)
    @JoinColumn(name="brand_id", nullable=false)
    private Long brand;

    @Column(name="name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToMany(mappedBy="categories")
    private Set<Product> products = new HashSet<>();
}
