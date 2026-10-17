package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;

import java.util.List;

/**
 * 建立線上訂單命令（新增，由 RabbitMQ 消費者發出）。
 *
 * 初始狀態固定為 AWAITING_ACCEPTANCE（不接受外部指定），
 * requestId（ticketId）作為訂單的 request_id，讓消費者重送時不會重複建立訂單。
 *
 * @param requestId    線上下單請求 ID（ticketId）
 * @param brandId      品牌 ID
 * @param storeId      分店 ID
 * @param memberId     會員 ID（會員下單時設定，訪客 / 員工為 null）
 * @param guestRef     訪客識別（"GUEST:uuid"，訪客下單時設定）
 * @param items        訂單品項
 * @param customerNote 顧客備註
 */
public record PlaceOnlineOrderCommand(
    String requestId,
    Long brandId,
    Long storeId,
    Long memberId,
    String guestRef,
    List<OrderItemDto> items,
    String customerNote
) {}
