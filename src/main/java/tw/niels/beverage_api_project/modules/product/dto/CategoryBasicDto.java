package tw.niels.beverage_api_project.modules.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;

@Data
@Schema(description = "商品分類基本資訊 (用於內嵌顯示)")
public class CategoryBasicDto {

    // 修改（P0-10）：TSID 超過 JS Number 安全整數範圍，JSON 回應改以字串輸出
    @Schema(description = "分類 ID", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    @Schema(description = "分類名稱", example = "茶類")
    private String name;

    // 修改：參數名稱原與型別同名（批次改名殘留），改為 entity
    public static CategoryBasicDto fromEntity(CategoryEntity entity) {
        if (entity == null) return null;
        CategoryBasicDto dto = new CategoryBasicDto();
        dto.setCategoryId(entity.getId());
        dto.setName(entity.getName());
        return dto;
    }
}
