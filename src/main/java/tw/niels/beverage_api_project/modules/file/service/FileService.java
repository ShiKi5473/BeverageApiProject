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

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    /** fileId 必須是 UUID 格式，避免路徑穿越或覆寫其他物件（S-6） */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** 檔名長度上限（含副檔名） */
    private static final int MAX_FILE_NAME_LENGTH = 100;

    /** 單檔分片數上限，需與 DTO 的 @Max 一致 */
    private static final int MAX_CHUNKS = 1000;

    /**
     * 合併時送給 MinIO 的 part 大小（10 MiB）。
     * MinIO 規定 multipart 非最後一個 part 至少 5 MiB；
     * 改用「串流 putObject（未知大小）」由 SDK 自行切 part，不再要求每個分片 >= 5 MiB。
     */
    private static final long MERGE_PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public FileService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上傳單個分片
     * 路徑格式: temp/{brandId}/{fileId}/{chunkNumber}
     * （S-6：物件 key 以 brandId 做租戶隔離，不同品牌的 fileId 不會互相覆寫）
     */
    public void uploadChunk(Long brandId, FileChunkUploadDto dto) {
        String fileId = validateFileId(dto.getFileId());
        MultipartFile file = dto.getFile();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("分片檔案不可為空");
        }

        String objectName = chunkObjectName(brandId, fileId, dto.getChunkNumber());
        // try-with-resources：確保輸入串流一定被關閉，避免連線／檔案描述子外洩
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            logger.debug("Chunk uploaded: {}", objectName);
        // 修正：使用 BadRequestException 取代 RuntimeException，讓 GlobalExceptionHandler 正確回傳 HTTP 400
        // 注意：MinIO SDK 拋出多種 checked exception，此處保留 catch(Exception) 以簡化處理
        } catch (Exception e) {
            logger.error("Failed to upload chunk", e);
            throw new BadRequestException("Chunk upload failed: " + e.getMessage());
        }
    }

    /**
     * 合併分片
     * 將 temp/{brandId}/{fileId}/1..N 依序串流合併為 uploads/{brandId}/{timestamp}_{fileName}
     *
     * 修正(S-6)：原本使用 MinIO composeObject，但它要求「除最後一個以外的來源都 >= 5 MiB」，
     * 而 Spring multipart 預設單檔上限僅 1 MB，所以多分片合併必定失敗。
     * 改為以 SequenceInputStream 依序讀取各分片，串成單一輸入串流後呼叫 putObject（大小未知），
     * 由 MinIO SDK 自動以 10 MiB 為單位切 multipart。
     */
    public String mergeChunks(Long brandId, FileMergeRequestDto dto) {
        String fileId = validateFileId(dto.getFileId());
        String safeFileName = sanitizeFileName(dto.getFileName());
        int totalChunks = validateTotalChunks(dto.getTotalChunks());

        // 1. 先確認所有分片都存在，缺漏時提早回報明確錯誤（比合併到一半才失敗好除錯）
        for (int i = 1; i <= totalChunks; i++) {
            String chunkObjectName = chunkObjectName(brandId, fileId, i);
            try {
                minioClient.statObject(
                        StatObjectArgs.builder().bucket(bucketName).object(chunkObjectName).build());
            } catch (Exception e) {
                throw new BadRequestException("缺少分片 " + i + "，請重新上傳後再合併");
            }
        }

        // 2. 決定最終檔案路徑（brandId 前綴 + 時間戳防止檔名衝突）
        String finalObjectName = "uploads/" + brandId + "/" + System.currentTimeMillis() + "_" + safeFileName;
        String contentType = URLConnection.guessContentTypeFromName(safeFileName);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // 3. 依序串流所有分片，寫入單一物件（size = -1 表示未知大小，由 SDK 以 partSize 切 part）
        try (InputStream merged = new SequenceInputStream(new ChunkEnumeration(brandId, fileId, totalChunks))) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(finalObjectName)
                            .stream(merged, -1, MERGE_PART_SIZE)
                            .contentType(contentType)
                            .build()
            );

            logger.info("File merged successfully: {}", finalObjectName);
        // 注意：MinIO putObject 可能拋出多種 checked exception，此處保留 catch(Exception) 涵蓋所有情況
        } catch (Exception e) {
            logger.error("Failed to merge file", e);
            throw new BadRequestException("File merge failed: " + e.getMessage());
        }

        // 4. 合併成功後清除暫存分片（失敗只記錄警告；殘留者由 FileCleanupSchedule 定期清理）
        cleanupChunks(brandId, fileId, totalChunks);

        // 5. 回傳 objectName 供後續業務邏輯存入 DB
        return finalObjectName;
    }

    /**
     * 惰性逐一開啟分片串流的 Enumeration，供 SequenceInputStream 使用。
     * 一次只開一個分片連線；SequenceInputStream 讀完（或關閉）當前串流後會自動關閉它。
     */
    private class ChunkEnumeration implements Enumeration<InputStream> {
        private final Long brandId;
        private final String fileId;
        private final int totalChunks;
        private int next = 1;

        ChunkEnumeration(Long brandId, String fileId, int totalChunks) {
            this.brandId = brandId;
            this.fileId = fileId;
            this.totalChunks = totalChunks;
        }

        @Override
        public boolean hasMoreElements() {
            return next <= totalChunks;
        }

        @Override
        public InputStream nextElement() {
            if (!hasMoreElements()) {
                throw new NoSuchElementException();
            }
            String chunkObjectName = chunkObjectName(brandId, fileId, next++);
            try {
                return minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucketName).object(chunkObjectName).build());
            } catch (Exception e) {
                // Enumeration 不能拋 checked exception，包成 UncheckedIOException 讓外層 catch 統一處理
                throw new UncheckedIOException(new IOException("讀取分片失敗: " + chunkObjectName, e));
            }
        }
    }

    /**
     * 清除指定 fileId 的所有分片
     */
    private void cleanupChunks(Long brandId, String fileId, int totalChunks) {
        try {
            for (int i = 1; i <= totalChunks; i++) {
                String chunkName = chunkObjectName(brandId, fileId, i);
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucketName).object(chunkName).build()
                );
            }
        // 清理分片失敗不影響主流程（合併已完成），僅記錄警告
        } catch (Exception e) {
            logger.warn("Failed to cleanup chunks for fileId: {}, reason: {}", fileId, e.getMessage());
        }
    }

    /**
     * 取得檔案的 Presigned URL (供前端預覽或下載)
     *
     * @return presigned URL；產生失敗時回傳 null（呼叫端不可直接用 Map.of 放入 null）
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
        // 取得 presigned URL 失敗時回傳 null，由呼叫端決定後續處理
        } catch (Exception e) {
            logger.warn("Failed to generate presigned URL for: {}", objectName, e);
            return null;
        }
    }

    // ─── 輸入驗證與命名輔助 ────────────────────────────────────────────────

    /** 分片物件 key：temp/{brandId}/{fileId}/{chunkNumber} */
    private static String chunkObjectName(Long brandId, String fileId, Integer chunkNumber) {
        if (chunkNumber == null || chunkNumber < 1 || chunkNumber > MAX_CHUNKS) {
            throw new BadRequestException("分片序號必須介於 1 到 " + MAX_CHUNKS);
        }
        return String.format("temp/%d/%s/%d", brandId, fileId, chunkNumber);
    }

    /** 驗證 fileId 為 UUID 格式（防止路徑穿越與任意 key 注入） */
    private static String validateFileId(String fileId) {
        if (fileId == null || !UUID_PATTERN.matcher(fileId).matches()) {
            throw new BadRequestException("fileId 必須是合法的 UUID");
        }
        return fileId;
    }

    private static int validateTotalChunks(Integer totalChunks) {
        if (totalChunks == null || totalChunks < 1 || totalChunks > MAX_CHUNKS) {
            throw new BadRequestException("totalChunks 必須介於 1 到 " + MAX_CHUNKS);
        }
        return totalChunks;
    }

    /**
     * 清理檔名：移除路徑成分（/ 與 \）、僅保留字母數字與 . _ - 空白，移除開頭的點，
     * 並限制長度（超過時保留副檔名）。清理後為空則拒絕。
     */
    static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            throw new BadRequestException("fileName 不可為空");
        }
        // 只取最後一段，丟棄任何目錄成分
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        // 非允許字元（含控制字元）一律換成底線
        name = name.replaceAll("[^\\p{L}\\p{N}._\\- ]", "_");
        // 移除開頭的點，避免產生隱藏檔或 ".." 之類的檔名
        name = name.replaceAll("^\\.+", "").trim();
        if (name.isEmpty()) {
            throw new BadRequestException("fileName 不合法");
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String ext = (dot > 0 && name.length() - dot <= 10) ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILE_NAME_LENGTH - ext.length()) + ext;
        }
        return name;
    }
}
