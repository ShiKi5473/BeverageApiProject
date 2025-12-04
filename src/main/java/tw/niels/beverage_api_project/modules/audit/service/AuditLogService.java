package tw.niels.beverage_api_project.modules.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.audit.entity.AuditLog;
import tw.niels.beverage_api_project.modules.audit.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 非同步儲存日誌，避免阻塞主執行緒
     */
    @Async
    public void saveLog(AuditLog log) {
        try {
            auditLogRepository.save(log);
            logger.debug("Audit log saved: {}", log.getAction());
        } catch (Exception e) {
            // 日誌寫入失敗不應影響主流程，僅記錄錯誤
            logger.error("Failed to save audit log", e);
        }
    }
}