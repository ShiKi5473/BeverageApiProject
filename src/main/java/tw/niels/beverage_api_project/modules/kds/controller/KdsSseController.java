package tw.niels.beverage_api_project.modules.kds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.kds.service.KdsService;

@Tag(name = "KDS (廚房顯示系統)", description = "KDS 與 POS 的即時通訊 API (Server-Sent Events)")
@RestController
@RequestMapping("/api/v1/kds")
public class KdsSseController {

    private final KdsService kdsService;
    private final ControllerHelperService controllerHelperService;

    public KdsSseController(KdsService kdsService, ControllerHelperService controllerHelperService) {
        this.kdsService = kdsService;
        this.controllerHelperService = controllerHelperService;
    }

    @Operation(summary = "訂閱訂單即時更新 (SSE)",
            description = "建立 SSE 長連線，當訂單狀態改變時，Server 會主動推播事件 data: {action, payload}。" +
                    "前端需先呼叫 POST /api/v1/auth/sse-ticket 取得一次性 ticket，" +
                    "再以 EventSource(/api/v1/kds/stream?ticket=xxx) 建立連線。每 25 秒送出一次心跳註解。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // 【新增】只有綁定分店的員工角色才需要 KDS／POS 即時推播（會員、訪客改用 STOMP 個人通知）
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF', 'TESTER')")
    public SseEmitter streamOrders() {
        // 身份驗證已由 JwtAuthenticationFilter 透過 SSE ticket 完成
        // 此處直接從 SecurityContext 取得當前使用者的分店 ID
        Long storeId = controllerHelperService.getCurrentStoreId();
        if (storeId == null) {
            // 【修正】未綁定分店（例如品牌管理員）原本會把 null 放進 ConcurrentHashMap → NullPointerException（500）
            throw new BadRequestException("此帳號未綁定分店，無法訂閱 KDS 即時更新");
        }
        return kdsService.subscribe(storeId);
    }
}
