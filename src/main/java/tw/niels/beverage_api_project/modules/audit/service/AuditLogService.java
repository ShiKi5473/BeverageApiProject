package tw.niels.beverage_api_project.modules.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.audit.entity.AuditLogEntity;
import tw.niels.beverage_api_project.modules.audit.repository.AuditLogJpaRepository;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogJpaRepository auditLogRepository;

    public AuditLogService(AuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 非同步儲存日誌，避免阻塞主執行緒
     * 改指定專用執行緒池 auditExecutor（由 AsyncConfig 建立），
     * 與 KDS 推播的執行緒池分開，避免稽核日誌塞滿佇列時影響即時推播。
     */
    @Async("auditExecutor")
    public void saveLog(AuditLogEntity log) {
        try {
            auditLogRepository.save(log);
            logger.debug("Audit log saved: {}", log.getAction());
        } catch (Exception e) {
            // 日誌寫入失敗不應影響主流程，僅記錄錯誤
            logger.error("Failed to save audit log", e);
        }
    }
}