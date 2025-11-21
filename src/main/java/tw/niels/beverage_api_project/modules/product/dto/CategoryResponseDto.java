package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.Category;

@Data
@Schema(description = "商品分類詳細資訊")
public class CategoryResponseDto {

    @Schema(description = "分類 ID", example = "1")
    private Long categoryId;

    @Schema(description = "分類名稱", example = "茶類")
    private String name;

    @Schema(description = "排序權重", example = "10")
    private Integer sortOrder;

    @Schema(description = "所屬品牌 ID", example = "1")
    private Long brandId;

    public static CategoryResponseDto fromEntity(Category category) {
        if (category == null) return null;

        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setSortOrder(category.getSortOrder());

        if (category.getBrand() != null) {
            dto.setBrandId(category.getBrand().getBrandId());
        }
        return dto;
    }
}