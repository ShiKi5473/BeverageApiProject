package tw.niels.beverage_api_project.modules.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;

@Data
@Schema(description = "商品分類詳細資訊")
public class CategoryResponseDto {

    // 修改（P0-10）：ID 以字串輸出，避免前端精度遺失
    @Schema(description = "分類 ID", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    @Schema(description = "分類名稱", example = "茶類")
    private String name;

    @Schema(description = "排序權重", example = "10")
    private Integer sortOrder;

    // 修改（P0-10）：品牌 ID 同樣以字串輸出
    @Schema(description = "所屬品牌 ID", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long brandId;

    // 修改：參數名稱原與型別同名（批次改名殘留），改為 entity
    public static CategoryResponseDto fromEntity(CategoryEntity entity) {
        if (entity == null) return null;

        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setCategoryId(entity.getId());
        dto.setName(entity.getName());
        dto.setSortOrder(entity.getSortOrder());

        if (entity.getBrand() != null) {
            dto.setBrandId(entity.getBrand().getId());
        }
        return dto;
    }
}
