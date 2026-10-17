package tw.niels.beverage_api_project.modules.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 線上訂單 ticket 回應 DTO（新增）。
 *
 * 用於：
 * - POST /api/v1/online-orders（202）：{ticketId, status:"QUEUED", message}
 * - GET  /api/v1/online-orders/{ticketId}：{ticketId, status, orderId, orderNumber, orderStatus, message}
 *
 * @param ticketId    線上下單請求 ID（同時是訂單的 request_id）
 * @param status      ticket 狀態：QUEUED / CREATED / FAILED
 * @param orderId     建立成功後的訂單 ID（字串輸出；尚未建立時為 null）
 * @param orderNumber 建立成功後的訂單號碼
 * @param orderStatus 訂單目前狀態（如 AWAITING_ACCEPTANCE、PREPARING）
 * @param message     給前端顯示的訊息（失敗時為原因）
 */
@Schema(description = "線上訂單請求（ticket）狀態")
public record OnlineOrderTicketResponseDto(
        @Schema(description = "Ticket ID") String ticketId,
        @Schema(description = "Ticket 狀態：QUEUED / CREATED / FAILED") String status,
        @Schema(description = "訂單 ID（字串）")
        @JsonSerialize(using = ToStringSerializer.class) Long orderId,
        @Schema(description = "訂單號碼") String orderNumber,
        @Schema(description = "訂單目前狀態") String orderStatus,
        @Schema(description = "訊息") String message
) {

    /** 建立「已排隊」的回應（POST 202 使用） */
    public static OnlineOrderTicketResponseDto queued(String ticketId, String message) {
        return new OnlineOrderTicketResponseDto(ticketId, "QUEUED", null, null, null, message);
    }
}
