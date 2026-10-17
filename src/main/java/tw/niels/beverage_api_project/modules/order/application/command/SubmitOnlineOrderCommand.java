package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;

import java.util.List;

/**
 * 送出線上訂單命令（新增，由 OnlineOrderController 發出）。
 *
 * 只負責驗證分店、建立 ticket 並把任務送進 RabbitMQ 佇列，實際建單由消費者非同步處理。
 *
 * @param brandId      品牌 ID（來自 token）
 * @param storeId      分店 ID（來自請求 body）
 * @param requester    下單者 principalKey（"U:{userId}" 或 "GUEST:uuid"），查詢 ticket 時驗證本人
 * @param memberId     會員 ID（會員下單時設定）
 * @param guestRef     訪客識別（訪客下單時設定）
 * @param items        訂單品項
 * @param customerNote 顧客備註
 */
public record SubmitOnlineOrderCommand(
    Long brandId,
    Long storeId,
    String requester,
    Long memberId,
    String guestRef,
    List<OrderItemDto> items,
    String customerNote
) {}
