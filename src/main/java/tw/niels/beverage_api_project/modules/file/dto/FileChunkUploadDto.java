package tw.niels.beverage_api_project.modules.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "檔案分片上傳請求")
public class FileChunkUploadDto {

    @Schema(description = "檔案唯一識別碼 (通常為前端生成的 UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String fileId;

    @Schema(description = "分片序號 (從 1 開始)", example = "1")
    private Integer chunkNumber;

    @Schema(description = "分片檔案實體")
    private MultipartFile file;
}