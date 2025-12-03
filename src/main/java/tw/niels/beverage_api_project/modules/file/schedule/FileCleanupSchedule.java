package tw.niels.beverage_api_project.modules.file.schedule;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class FileCleanupSchedule {

    private static final Logger logger = LoggerFactory.getLogger(FileCleanupSchedule.class);
    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public FileCleanupSchedule(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 每天凌晨 04:00 清理超過 24 小時的 temp 暫存分片
     * (假設這些分片是因為上傳中斷而殘留的垃圾)
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "FileTempCleanup", lockAtMostFor = "30m")
    public void cleanupTempFiles() {
        logger.info("開始清理 MinIO 過期暫存分片...");
        try {
            // 列出 temp/ 目錄下的所有物件 (遞迴)
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix("temp/")
                            .recursive(true)
                            .build()
            );

            ZonedDateTime threshold = ZonedDateTime.now().minusHours(24);
            int deletedCount = 0;

            for (Result<Item> result : results) {
                Item item = result.get();
                // 檢查最後修改時間
                if (item.lastModified().isBefore(threshold)) {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(item.objectName())
                                    .build()
                    );
                    deletedCount++;
                }
            }
            logger.info("MinIO 清理完成，共刪除 {} 個過期分片。", deletedCount);

        } catch (Exception e) {
            logger.error("MinIO 清理任務失敗", e);
        }
    }
}