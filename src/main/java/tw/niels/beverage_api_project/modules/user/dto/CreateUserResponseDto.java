package tw.niels.beverage_api_project.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "建立使用者回應")
public class CreateUserResponseDto {

    @Schema(description = "執行結果訊息", example = "Brand Admin created successfully...")
    private String message;

    @Schema(description = "新建立的使用者 ID", example = "10")
    private Long userId;
}