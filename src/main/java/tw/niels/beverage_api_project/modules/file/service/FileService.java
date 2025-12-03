package tw.niels.beverage_api_project.modules.file.service;

import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.file.dto.FileChunkUploadDto;
import tw.niels.beverage_api_project.modules.file.dto.FileMergeRequestDto;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public FileService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上傳單個分片
     * 路徑格式: temp/{fileId}/{chunkNumber}
     */
    public void uploadChunk(FileChunkUploadDto dto) {
        try {
            String objectName = String.format("temp/%s/%d", dto.getFileId(), dto.getChunkNumber());
            MultipartFile file = dto.getFile();
            InputStream inputStream = file.getInputStream();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            logger.debug("Chunk uploaded: {}", objectName);
        } catch (Exception e) {
            logger.error("Failed to upload chunk", e);
            throw new RuntimeException("Chunk upload failed: " + e.getMessage());
        }
    }

    /**
     * 合併分片
     * 將 temp/{fileId}/* 的所有分片合併為 uploads/{fileName}
     */
    public String mergeChunks(FileMergeRequestDto dto) {
        try {
            List<ComposeSource> sources = new ArrayList<>();

            // 1. 檢查並準備所有分片來源
            for (int i = 1; i <= dto.getTotalChunks(); i++) {
                String chunkObjectName = String.format("temp/%s/%d", dto.getFileId(), i);

                // 這裡可以加入檢查分片是否存在的邏輯 (statObject)
                // 為了效能，直接加入來源，若不存在 compose 時會報錯
                sources.add(
                        ComposeSource.builder()
                                .bucket(bucketName)
                                .object(chunkObjectName)
                                .build()
                );
            }

            // 2. 決定最終檔案路徑 (例如加上時間戳或 UUID 防止檔名衝突)
            String finalObjectName = "uploads/" + System.currentTimeMillis() + "_" + dto.getFileName();

            // 3. 執行合併 (ComposeObject)
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(finalObjectName)
                            .sources(sources)
                            .build()
            );

            logger.info("File merged successfully: {}", finalObjectName);

            // 4. (非同步) 清除暫存分片
            // 這裡簡單實作：直接呼叫刪除，實際生產環境建議丟給 RabbitMQ 或排程處理
            cleanupChunks(dto.getFileId(), dto.getTotalChunks());

            // 5. 回傳可存取的 URL (如果是 public bucket，直接回傳路徑；私有 bucket 則需 presigned URL)
            // 這裡回傳 objectName 供後續業務邏輯存入 DB
            return finalObjectName;

        } catch (Exception e) {
            logger.error("Failed to merge file", e);
            throw new BadRequestException("File merge failed: " + e.getMessage());
        }
    }

    /**
     * 清除指定 fileId 的所有分片
     */
    private void cleanupChunks(String fileId, int totalChunks) {
        try {
            for (int i = 1; i <= totalChunks; i++) {
                String chunkName = String.format("temp/%s/%d", fileId, i);
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucketName).object(chunkName).build()
                );
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup chunks for fileId: {}", fileId);
        }
    }

    /**
     * 取得檔案的 Presigned URL (供前端預覽或下載)
     */
    public String getFileUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(60 * 60) // 1 小時有效
                            .build()
            );
        } catch (Exception e) {
            return null;
        }
    }
}