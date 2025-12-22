package tw.niels.beverage_api_project.modules.product.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

@Entity
@Getter
@Setter
@Table(name = "option_groups", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "brand_id", "name" })
})
@AttributeOverride(name = "id", column = @Column(name = "group_id"))
public class OptionGroup extends BaseTsidEntity {

    public OptionGroup() {
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 20)
    private SelectionType selectionType;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ProductOption> options = new HashSet<>();

}
