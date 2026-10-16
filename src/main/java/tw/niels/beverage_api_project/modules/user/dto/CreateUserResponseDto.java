package tw.niels.beverage_api_project.modules.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    // ID 以字串輸出（TSID 約 8.6e17，超過 JS Number.MAX_SAFE_INTEGER 會失真）
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
}