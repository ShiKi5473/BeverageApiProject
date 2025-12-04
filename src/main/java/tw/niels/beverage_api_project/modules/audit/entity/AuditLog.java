package tw.niels.beverage_api_project.modules.audit.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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

    // Getters & Setters (Lombok @Data 也可以，這裡手寫以示範)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public boolean isSuccess() { return isSuccess; }
    public void setSuccess(boolean success) { isSuccess = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}