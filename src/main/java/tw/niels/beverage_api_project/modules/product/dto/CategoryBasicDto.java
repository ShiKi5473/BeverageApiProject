package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.Category;

@Data
@Schema(description = "商品分類基本資訊 (用於內嵌顯示)")
public class CategoryBasicDto {

    @Schema(description = "分類 ID", example = "1")
    private Long categoryId;

    @Schema(description = "分類名稱", example = "茶類")
    private String name;

    public static CategoryBasicDto fromEntity(Category category) {
        if (category == null) return null;
        CategoryBasicDto dto = new CategoryBasicDto();
        dto.setCategoryId(category.getId());
        dto.setName(category.getName());
        return dto;
    }
}