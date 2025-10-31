package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import tw.niels.beverage_api_project.modules.product.entity.Product;

public class ProductPosDto {
    private Long id;

    private String name;

    private BigDecimal basePrice;

    Set<OptionGroupResponseDto> optionGroups;

    Set<CategoryBasicDto> categories;

    public static ProductPosDto fromEntity(Product product) {
        if (product == null)
            return null;

        ProductPosDto dto = new ProductPosDto();
        dto.setId(product.getProductId());
        dto.setName(product.getName()); // 這會使用 Product 的 String name
        dto.setBasePrice(product.getBasePrice());

        // 轉換 OptionGroups
        if (product.getOptionGroups() != null) {
            dto.setOptionGroups(
                    product.getOptionGroups().stream()
                            .map(OptionGroupResponseDto::fromEntity)
                            .collect(Collectors.toSet()));
        }

        if (product.getCategories() != null) {
            dto.setCategories(
                    product.getCategories().stream()
                            .map(CategoryBasicDto::fromEntity)
                            .collect(Collectors.toSet()));
        }
        return dto;
    }

    public ProductPosDto(BigDecimal basePrice, Long id, String name, Set<OptionGroupResponseDto> optionGroups,
            Set<CategoryBasicDto> categories) {
        this.basePrice = basePrice;
        this.id = id;
        this.name = name;
        this.optionGroups = optionGroups;
        this.categories = categories;
    }

    public ProductPosDto() {
    }

    public Set<CategoryBasicDto> getCategories() {
        return categories;
    }

    public void setCategories(Set<CategoryBasicDto> categories) {
        this.categories = categories;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Set<OptionGroupResponseDto> getOptionGroups() {
        return optionGroups;
    }

    public void setOptionGroups(Set<OptionGroupResponseDto> optionGroups) {
        this.optionGroups = optionGroups;
    }
}
