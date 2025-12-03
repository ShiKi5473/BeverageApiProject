package tw.niels.beverage_api_project.modules.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "檔案合併請求")
public class FileMergeRequestDto {

    @Schema(description = "檔案唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000")
    private String fileId;

    @Schema(description = "原始檔案名稱 (含副檔名)", example = "menu-2025.jpg")
    private String fileName;

    @Schema(description = "總分片數", example = "5")
    private Integer totalChunks;
}