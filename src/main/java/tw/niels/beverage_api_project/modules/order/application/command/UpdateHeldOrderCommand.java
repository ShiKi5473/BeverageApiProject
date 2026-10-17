package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.List;
import java.util.Objects;

/**
 * 更新暫存/待處理訂單命令
 *
 * 修改說明（M-1 分店資料隔離）：新增 storeScope（呼叫者可操作的分店範圍），
 * UpdateHeldOrderHandler 載入訂單後驗證訂單所屬分店，綁店員工不可修改其他分店的訂單（403）。
 *
 * @param brandId    品牌 ID
 * @param orderId    訂單 ID
 * @param status     目標狀態（PENDING / HELD）
 * @param items      新的訂單品項
 * @param storeScope 呼叫者可操作的分店範圍（不可為 null；全品牌請用 StoreScope.brandWide()）
 */
public record UpdateHeldOrderCommand(
    Long brandId,
    Long orderId,
    OrderStatus status,
    List<OrderItemDto> items,
    StoreScope storeScope
) {
    /** 新增：強制帶入分店範圍，避免呼叫端遺漏而跳過分店檢查 */
    public UpdateHeldOrderCommand {
        Objects.requireNonNull(storeScope, "storeScope 不可為 null");
    }
}
