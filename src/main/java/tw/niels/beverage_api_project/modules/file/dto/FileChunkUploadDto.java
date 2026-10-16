package tw.niels.beverage_api_project.modules.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "檔案分片上傳請求")
public class FileChunkUploadDto {

    // 驗證 fileId 必須是 UUID，避免路徑穿越／任意 key 注入(S-6)
    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "fileId 必須是合法的 UUID")
    @Schema(description = "檔案唯一識別碼 (通常為前端生成的 UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String fileId;

    @NotNull
    @Min(1)
    @Max(1000)
    @Schema(description = "分片序號 (從 1 開始)", example = "1")
    private Integer chunkNumber;

    @NotNull
    @Schema(description = "分片檔案實體")
    private MultipartFile file;
}
