package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

public class CreateProductRequestDto {
    @NotBlank(message = "商品名不可為空")
    private String name;

    private String description;

    @NotNull(message = "商品價格不可為空")
    @DecimalMin(value = "0.0", inclusive = false, message = "價格不可小於0")
    private BigDecimal basePrice;

    private String imageUrl;

    @NotNull(message = "必須指定商品是否可用")
    private ProductStatus status;

    @NotBlank(message = "必須為商品指定一個類別")
    private Set<Long> categoryIds;

    // getter and setter

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

    public Set<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(Set<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

}
