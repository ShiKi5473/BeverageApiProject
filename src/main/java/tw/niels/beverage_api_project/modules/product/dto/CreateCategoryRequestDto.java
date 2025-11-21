package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "建立商品分類請求")
public class CreateCategoryRequestDto {

    @NotBlank(message = "分類名稱不可為空")
    @Schema(description = "分類名稱", example = "經典奶茶系列")
    private String name;

    @Schema(description = "排序權重 (越小越前面)", example = "1")
    private Integer sortOrder;

}