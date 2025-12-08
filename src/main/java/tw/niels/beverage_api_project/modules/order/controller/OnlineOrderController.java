package tw.niels.beverage_api_project.modules.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.niels.beverage_api_project.common.annotation.Idempotent;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.common.util.TsidUtil;
import tw.niels.beverage_api_project.config.RabbitConfig;
import tw.niels.beverage_api_project.modules.order.dto.AsyncOrderTaskDto;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;

import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/online-orders")
@Tag(name = "Online Order", description = "線上點餐 API (非同步處理)")
public class OnlineOrderController {

    private static final Logger logger = LoggerFactory.getLogger(OnlineOrderController.class);

    private final RabbitTemplate rabbitTemplate;
    private final ControllerHelperService helperService;

    public OnlineOrderController(RabbitTemplate rabbitTemplate, ControllerHelperService helperService) {
        this.rabbitTemplate = rabbitTemplate;
        this.helperService = helperService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER', 'STAFF', 'MANAGER', 'BRAND_ADMIN')") // 會員也可使用
    @Idempotent // 冪等性檢查
    @Operation(summary = "建立線上訂單 (非同步)", description = "將訂單請求放入佇列排隊，並立即回傳 Ticket ID 供後續查詢")
    public ResponseEntity<Map<String, String>> createOnlineOrder(
            @Valid @RequestBody CreateOrderRequestDto requestDto) {

        // 1. 取得上下文資訊
        Long brandId = helperService.getCurrentBrandId();
        Long storeId = helperService.getCurrentStoreId(); // 線上點餐時，前端通常需帶入 storeId，這邊假設已在 Token 或 Context 中
        Long userId = helperService.getCurrentUserId();

        // 對於純會員 (MEMBER)，Context 可能沒有 StoreId，這部分需依據您的業務邏輯調整
        // 這裡假設前端在 Header 或其他方式已讓 helperService 能解析到目標 StoreId
        // 若 helperService 沒辦法從 Token 拿到 StoreId (例如會員跨店點餐)，則 requestDto 應該包含 storeId
        // 暫時做個防呆：
        if (storeId == null) {
            throw new BadRequestException("無法識別目標分店，請確認請求參數或 Token");
        }

        // 2. 生成 Ticket ID (使用 TSID)
        String ticketId = TsidUtil.nextIdString();

        // 3. 組裝任務 DTO
        AsyncOrderTaskDto task = new AsyncOrderTaskDto(
                ticketId,
                brandId,
                storeId,
                userId,
                requestDto
        );

        // 4. 發送到 RabbitMQ
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.ONLINE_ORDER_EXCHANGE,
                    RabbitConfig.ONLINE_ORDER_ROUTING_KEY,
                    task
            );
            logger.info("線上訂單請求已發送至佇列，Ticket ID: {}", ticketId);
        } catch (Exception e) {
            logger.error("發送 RabbitMQ 失敗", e);
            throw new RuntimeException("系統繁忙，請稍後再試");
        }

        // 5. 立即回傳 202 Accepted
        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "訂單處理中",
                        "ticketId", ticketId,
                        "status", "QUEUED"
                ));
    }
}