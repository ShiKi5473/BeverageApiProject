package tw.niels.beverage_api_project.modules.audit.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "audit_logs") // 對應 MongoDB 的 Collection
public class AuditLog {

    @Id
    private String id;

    private String traceId;      // 分散式追蹤 ID (Zipkin)
    private Long operatorId;     // 操作者 User ID
    private String operatorName; // 操作者名稱/手機
    private String action;       // 動作名稱 (e.g., "UPDATE_STAFF")
    private String targetResource; // 目標資源 (e.g., "User: 10")
    private String requestParams; // 請求參數 (JSON 摘要)
    private boolean isSuccess;    // 執行結果
    private String errorMessage;  // 若失敗，錯誤訊息
    private Instant timestamp;    // 發生時間

    // Constructors
    public AuditLog() {}


}