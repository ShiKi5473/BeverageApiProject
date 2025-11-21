package tw.niels.beverage_api_project.modules.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;

@Data
@Schema(description = "建立選項群組請求 (如: 甜度、冰塊)")
public class CreateOptionGroupRequestDto {

    @NotBlank(message = "選項群組名稱不可為空")
    @Schema(description = "群組名稱", example = "冰塊調整")
    private String name;

    @NotNull(message = "必須指定單選或多選")
    @Schema(description = "選擇類型 (SINGLE/MULTIPLE)", example = "SINGLE")
    private SelectionType selectionType;

    @Schema(description = "排序權重", example = "1")
    private Integer sortOrder = 0;

}
