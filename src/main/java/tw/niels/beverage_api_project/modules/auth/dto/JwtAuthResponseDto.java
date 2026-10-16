package tw.niels.beverage_api_project.modules.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "JWT 認證回應")
public class JwtAuthResponseDto {
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Token 類型", example = "Bearer")
    private String tokenType = "Bearer";

    // 【修正】TSID 超過 JS Number.MAX_SAFE_INTEGER，回應一律以字串輸出避免前端精度遺失（null 仍輸出 null）
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "分店 ID (若為店員則回傳，管理員則為 null)", example = "1", type = "string")
    private Long storeId;

    @Schema(description = "使用者角色", example = "ROLE_STAFF")
    private String role;

    public JwtAuthResponseDto(String accessToken) {
        this.accessToken = accessToken;
        this.storeId = null;
    }
}
