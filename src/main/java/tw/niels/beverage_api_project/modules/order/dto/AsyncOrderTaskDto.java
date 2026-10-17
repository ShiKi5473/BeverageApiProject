package tw.niels.beverage_api_project.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 非同步訂單任務 DTO
 * 用於在 RabbitMQ 中傳遞建立線上訂單所需的資訊
 *
 * 修改說明（B-4 線上訂單）：
 * - 移除 userId / userPhone / orderRequest（原本把員工欄位當會員寫入、且由前端決定初始狀態）。
 * - 改傳 requester（下單者的 principalKey，用於 ticket 權限檢查）、memberId（會員下單）、
 *   guestRef（訪客下單，"GUEST:uuid"）、storeId、items 與 customerNote；
 *   初始狀態固定由後端決定為 AWAITING_ACCEPTANCE，不再接受前端傳入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncOrderTaskDto implements Serializable {

    // 用於冪等性檢查與追蹤 (Ticket ID)，消費者以此作為訂單的 request_id
    private String requestId;

    // 下單的品牌與分店
    private Long brandId;
    private Long storeId;

    // 新增：下單者的 principalKey（會員 / 員工為 "U:{userId}"，訪客為 "GUEST:uuid"）
    private String requester;

    // 新增：會員下單時的會員 ID（訪客與員工為 null）
    private Long memberId;

    // 新增：訪客下單時的訪客識別（"GUEST:uuid"，會員與員工為 null）
    private String guestRef;

    // 新增：訂單品項（取代原本整個 CreateOrderRequestDto，不再夾帶 status）
    private List<OrderItemDto> items;

    // 新增：顧客備註
    private String customerNote;
}
