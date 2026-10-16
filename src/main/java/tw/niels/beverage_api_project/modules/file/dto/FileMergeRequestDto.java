package tw.niels.beverage_api_project.modules.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "檔案合併請求")
public class FileMergeRequestDto {

    // 驗證 fileId 必須是 UUID(S-6)
    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "fileId 必須是合法的 UUID")
    @Schema(description = "檔案唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000")
    private String fileId;

    // 檔名長度限制；路徑成分與特殊字元由 FileService 進一步清理
    @NotBlank
    @Size(max = 200)
    @Schema(description = "原始檔案名稱 (含副檔名)", example = "menu-2025.jpg")
    private String fileName;

    @NotNull
    @Min(1)
    @Max(1000)
    @Schema(description = "總分片數 (1~1000)", example = "5")
    private Integer totalChunks;
}
