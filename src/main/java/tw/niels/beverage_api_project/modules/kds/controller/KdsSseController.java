package tw.niels.beverage_api_project.modules.kds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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

    @Operation(summary = "訂閱訂單即時更新 (SSE)", description = "建立 SSE 長連線，當訂單狀態改變時，Server 會主動推播事件。前端需使用 EventSource 連線。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrders(@RequestParam(required = false) String token) {
        // 1. 取得當前操作人員所屬的分店 ID
        // (假設 ControllerHelperService 會從 SecurityContext 或 Token 解析)
        Long storeId = controllerHelperService.getCurrentStoreId();

        // 2. 訂閱 SSE
        return kdsService.subscribe(storeId);
    }
}