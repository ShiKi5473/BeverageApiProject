package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;

import tw.niels.beverage_api_project.modules.product.entity.Product;

public class ProductSummaryDto {
    private Long id;

    private String name;

    private BigDecimal basePrice;
    private String imgUrl;
    private String description;

    public ProductSummaryDto() {
    }

    public ProductSummaryDto(Long id, String name, BigDecimal basePrice, String imgUrl, String description) {
        this.basePrice = basePrice;
        this.description = description;
        this.id = id;
        this.imgUrl = imgUrl;
        this.name = name;
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

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static ProductSummaryDto fromEntity(Product product) {
        if (product == null)
            return null;
        return new ProductSummaryDto(
                product.getProductId(),
                product.getName(),
                product.getBasePrice(),
                product.getImageUrl(),
                product.getDescription());
    }

}