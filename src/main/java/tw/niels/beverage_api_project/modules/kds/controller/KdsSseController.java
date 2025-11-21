package tw.niels.beverage_api_project.modules.kds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.kds.service.KdsService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.KDS)
@Tag(name = "KDS Real-time", description = "廚房顯示系統 SSE 連線端點")
public class KdsSseController {

    private final KdsService kdsService;
    private final ControllerHelperService helperService;

    public  KdsSseController(final KdsService kdsService, final ControllerHelperService helperService) {
        this.kdsService = kdsService;
        this.helperService = helperService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立 SSE 連線", description = "前端 KDS 頁面透過此端點建立長連線，接收訂單狀態變更通知")
    public SseEmitter streamKdsEvents() {
        // 驗證：確保是員工且有 StoreId
        Long storeId = helperService.getCurrentStoreId();
        if (storeId == null) {
            throw new BadRequestException("無效的店家 ID");
        }

        // 建立 Emitter，設定超時時間 (例如 30 分鐘)
        SseEmitter emitter = new SseEmitter(1800000L);

        // 註冊到 Service
        kdsService.addEmitter(storeId, emitter);

        return emitter;
    }
}
