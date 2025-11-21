package tw.niels.beverage_api_project.modules.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "建立品牌請求")
public class CreateBrandRequestDto {

    @NotEmpty
    @Schema(description = "品牌名稱", example = "品茶軒")
    private String name;

    @Schema(description = "聯絡人姓名", example = "張老闆")
    private String contactPerson;

}