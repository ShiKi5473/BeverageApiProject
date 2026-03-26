package tw.niels.beverage_api_project.modules.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;

@Data
@Schema(description = "商品詳細資訊回應")
public class ProductResponseDto {

    @Schema(description = "商品 ID", example = "101")
    private Long productId;

    @Schema(description = "商品名稱", example = "珍珠奶茶")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "基本價格", example = "50.00")
    private BigDecimal basePrice;

    @Schema(description = "圖片 URL")
    private String imageUrl;

    @Schema(description = "商品狀態", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "所屬分類列表")
    private Set<CategoryBasicDto> categories;

    @Schema(description = "關聯的客製化選項群組")
    private Set<OptionGroupResponseDto> optionGroups;

    public static ProductResponseDto fromEntity(ProductEntity ProductEntity) {
        if (ProductEntity == null) return null;
        ProductResponseDto dto = new ProductResponseDto();
        dto.setProductId(ProductEntity.getId());
        dto.setName(ProductEntity.getName());
        dto.setDescription(ProductEntity.getDescription());
        dto.setBasePrice(ProductEntity.getBasePrice());
        dto.setImageUrl(ProductEntity.getImageUrl());
        dto.setStatus(ProductEntity.getStatus());

        if (ProductEntity.getCategories() != null) {
            dto.setCategories(ProductEntity.getCategories().stream()
                    .map(CategoryBasicDto::fromEntity)
                    .collect(Collectors.toSet()));
        }

        if (ProductEntity.getOptionGroups() != null) {
            dto.setOptionGroups(ProductEntity.getOptionGroups().stream()
                    .map(OptionGroupResponseDto::fromEntity)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}