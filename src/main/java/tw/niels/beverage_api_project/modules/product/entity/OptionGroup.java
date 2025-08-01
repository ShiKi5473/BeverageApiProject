package tw.niels.beverage_api_project.modules.product.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="option_groups", uniqueConstraints={
    @UniqueConstraint(columnNames={"brand_id", "name"})
})
public class OptionGroup {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name="group_id")
    private Long group_id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="brand_id", nullable=false)
    private Brand brand;

    @Column(name="name", nullable=false, length=50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name="selection_type", nullable=false, length= 20)
    private SelectionType selectionType;

    @Column(name="sort_order")
    private Integer sortOrder;

    @OneToMany(mappedBy="optionGroup",
        cascade=CascadeType.ALL,
        orphanRemoval=true,
        fetch=FetchType.LAZY )
    private Set<ProductOption> options = new HashSet<>();
}
