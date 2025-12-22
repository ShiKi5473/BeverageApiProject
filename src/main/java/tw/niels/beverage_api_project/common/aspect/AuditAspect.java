package tw.niels.beverage_api_project.common.aspect;

import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.audit.entity.AuditLog;
import tw.niels.beverage_api_project.modules.audit.service.AuditLogService;
import tw.niels.beverage_api_project.security.AppUserDetails;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@Aspect
@Component
public class AuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogService auditLogService;
    private final ControllerHelperService helperService;
    private final Tracer tracer; // 用於獲取 Trace ID

    public AuditAspect(AuditLogService auditLogService, ControllerHelperService helperService, Tracer tracer) {
        this.auditLogService = auditLogService;
        this.helperService = helperService;
        this.tracer = tracer;
    }

    @Around("@annotation(audit)")
    public Object logAudit(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        Object result;

        // 1. 執行目標方法
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            success = false;
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            // 2. 蒐集資訊並非同步寫入日誌 (無論成功失敗)
            try {
                recordLog(joinPoint, audit, success, errorMsg);
            } catch (Exception e) {
                logger.error("Error creating audit log record", e);
            }
        }
    }

    private void recordLog(ProceedingJoinPoint joinPoint, Audit audit, boolean success, String errorMsg) {
        AuditLog log = new AuditLog();
        log.setTimestamp(Instant.now());
        log.setAction(audit.action());
        log.setSuccess(success);
        log.setErrorMessage(errorMsg);

        // 取得當前使用者資訊
        try {
            AppUserDetails user = helperService.getCurrentUserDetails();
            log.setOperatorId(user.userId());
            log.setOperatorName(user.getUsername());
        } catch (Exception e) {
            log.setOperatorName("Anonymous/System");
        }

        // 取得 Zipkin Trace ID
        if (tracer.currentSpan() != null) {
            log.setTraceId(Objects.requireNonNull(tracer.currentSpan()).context().traceId());
        }

        // 紀錄參數 (簡化處理，實際專案可能需要過濾敏感資訊如密碼)
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            log.setRequestParams(Arrays.toString(args));
        }

        // 紀錄目標 (例如方法名稱)
        log.setTargetResource(joinPoint.getSignature().toShortString());

        // 呼叫 Service 寫入 MongoDB
        auditLogService.saveLog(log);
    }
}