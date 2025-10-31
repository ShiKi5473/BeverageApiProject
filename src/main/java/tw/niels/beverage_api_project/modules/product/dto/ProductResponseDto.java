package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

public class ProductResponseDto {

    private Long productId;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String imageUrl;
    private ProductStatus status;
    private Set<CategoryBasicDto> categories;
    private Set<OptionGroupResponseDto> optionGroups;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Set<CategoryBasicDto> getCategories() {
        return categories;
    }

    public void setCategories(Set<CategoryBasicDto> categories) {
        this.categories = categories;
    }

    public Set<OptionGroupResponseDto> getOptionGroups() {
        return optionGroups;
    }

    public void setOptionGroups(Set<OptionGroupResponseDto> optionGroups) {
        this.optionGroups = optionGroups;
    }

    // 內部類別，只包含 Category 的基本資訊

    public static ProductResponseDto fromEntity(Product product) {
        if (product == null)
            return null;
        ProductResponseDto dto = new ProductResponseDto();
        dto.setProductId(product.getProductId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setBasePrice(product.getBasePrice());
        dto.setImageUrl(product.getImageUrl());
        dto.setStatus(product.getStatus());

        // 將 Category 轉換為 CategoryBasicDto
        if (product.getCategories() != null) {
            dto.setCategories(product.getCategories().stream()
                    .map(CategoryBasicDto::fromEntity)
                    .collect(Collectors.toSet()));
        }

        if (product.getOptionGroups() != null) {
            dto.setOptionGroups(product.getOptionGroups().stream()
                    .map(OptionGroupResponseDto::fromEntity)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}