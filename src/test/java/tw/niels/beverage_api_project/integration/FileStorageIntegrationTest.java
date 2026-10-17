package tw.niels.beverage_api_project.integration;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.config.DataSeeder;
import tw.niels.beverage_api_project.config.TestDataSourceConfig;
import tw.niels.beverage_api_project.modules.file.dto.FileChunkUploadDto;
import tw.niels.beverage_api_project.modules.file.dto.FileMergeRequestDto;
import tw.niels.beverage_api_project.modules.file.service.FileService;
import tw.niels.beverage_api_project.modules.report.schedule.ReportRecoveryRunner;
import tw.niels.beverage_api_project.modules.report.schedule.ReportScheduler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 檔案分片上傳／合併的整合測試（新增）。
 *
 * 目的：整合測試的物件儲存由 MinIO 改為 RustFS（MinIO 已停止公開提供 Docker 映像），
 * 以實際的上傳 → 合併 → 讀取 → 預簽網址 → 清除分片流程，驗證 MinIO Java SDK 用到的
 * S3 操作（putObject 含 multipart、statObject、getObject、removeObject、presigned URL）都相容。
 */
@Import(TestDataSourceConfig.class)
public class FileStorageIntegrationTest extends AbstractIntegrationTest {

    private static final int MIB = 1024 * 1024;

    // 與其他整合測試相同的 MockitoBean 組合，讓 Spring 測試 context 可以共用、不必重新啟動
    @MockitoBean private DataSeeder dataSeeder;
    @MockitoBean private ReportRecoveryRunner reportRecoveryRunner;
    @MockitoBean private ReportScheduler reportScheduler;

    @Autowired private FileService fileService;
    @Autowired private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Test
    @DisplayName("分片上傳後合併：合併檔超過 10 MiB 走 multipart，內容一致、分片已清除、預簽網址可下載")
    void uploadAndMergeChunks_ShouldProduceIdenticalObject() throws Exception {
        Long brandId = 42L;
        String fileId = UUID.randomUUID().toString();

        // 兩個 6 MiB + 一個小分片：合併後約 12 MiB，超過 FileService 的 10 MiB part 大小，
        // 可驗證「大小未知的串流 putObject」會以 multipart upload 寫入
        Random random = new Random(20261017L);
        List<byte[]> chunks = List.of(randomBytes(random, 6 * MIB), randomBytes(random, 6 * MIB), randomBytes(random, 1000));
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (int i = 0; i < chunks.size(); i++) {
            fileService.uploadChunk(brandId, chunkDto(fileId, i + 1, chunks.get(i)));
            expected.write(chunks.get(i));
        }

        String objectName = fileService.mergeChunks(brandId, mergeDto(fileId, "menu.png", chunks.size()));

        // 物件 key 以 brandId 做租戶隔離（S-6）
        assertThat(objectName).startsWith("uploads/" + brandId + "/").endsWith("_menu.png");

        // 大小與 Content-Type 正確
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
        assertThat(stat.size()).isEqualTo(expected.size());
        assertThat(stat.contentType()).isEqualTo("image/png");

        // 內容與分片依序串接的結果完全相同
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            assertThat(in.readAllBytes()).isEqualTo(expected.toByteArray());
        }

        // 合併後暫存分片已刪除
        for (int i = 1; i <= chunks.size(); i++) {
            String chunkName = "temp/" + brandId + "/" + fileId + "/" + i;
            assertThatThrownBy(() -> minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(chunkName).build()))
                    .isInstanceOf(ErrorResponseException.class);
        }

        // 預簽網址（SigV4 query 簽章）可直接以 HTTP GET 下載
        String url = fileService.getFileUrl(objectName);
        assertThat(url).isNotNull();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expected.toByteArray());
    }

    @Test
    @DisplayName("合併時缺少分片：回報缺少的分片序號，不產生合併檔")
    void mergeWithMissingChunk_ShouldFail() {
        Long brandId = 42L;
        String fileId = UUID.randomUUID().toString();
        fileService.uploadChunk(brandId, chunkDto(fileId, 1, "part-1".getBytes()));

        assertThatThrownBy(() -> fileService.mergeChunks(brandId, mergeDto(fileId, "notes.txt", 2)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("缺少分片 2");
    }

    private static byte[] randomBytes(Random random, int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private static FileChunkUploadDto chunkDto(String fileId, int chunkNumber, byte[] content) {
        FileChunkUploadDto dto = new FileChunkUploadDto();
        dto.setFileId(fileId);
        dto.setChunkNumber(chunkNumber);
        dto.setFile(new MockMultipartFile("file", "chunk-" + chunkNumber, "application/octet-stream", content));
        return dto;
    }

    private static FileMergeRequestDto mergeDto(String fileId, String fileName, int totalChunks) {
        FileMergeRequestDto dto = new FileMergeRequestDto();
        dto.setFileId(fileId);
        dto.setFileName(fileName);
        dto.setTotalChunks(totalChunks);
        return dto;
    }
}
