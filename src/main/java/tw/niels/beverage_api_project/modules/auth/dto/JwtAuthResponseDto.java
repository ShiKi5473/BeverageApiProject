package tw.niels.beverage_api_project.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "JWT 認證回應")
public class JwtAuthResponseDto {
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Token 類型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "分店 ID (若為店員則回傳，管理員則為 null)", example = "1")
    private Long storeId;

    @Schema(description = "使用者角色", example = "ROLE_STAFF")
    private String role;

    public JwtAuthResponseDto(String accessToken) {
        this.accessToken = accessToken;
        this.storeId = null;
    }
}
