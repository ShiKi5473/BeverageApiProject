package tw.niels.beverage_api_project.modules.order.application.command;

import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.util.Objects;

/**
 * 更新訂單狀態命令
 *
 * 修改說明（M-1 分店資料隔離）：新增 storeScope（呼叫者可操作的分店範圍），
 * UpdateOrderStatusHandler 載入訂單後驗證訂單所屬分店，綁店員工不可取消 / 結案其他分店的訂單（403）。
 *
 * @param brandId    品牌 ID
 * @param orderId    訂單 ID
 * @param status     目標狀態
 * @param storeScope 呼叫者可操作的分店範圍（不可為 null；全品牌請用 StoreScope.brandWide()）
 */
public record UpdateOrderStatusCommand(
    Long brandId,
    Long orderId,
    OrderStatus status,
    StoreScope storeScope
) {
    /** 新增：強制帶入分店範圍，避免呼叫端遺漏而跳過分店檢查 */
    public UpdateOrderStatusCommand {
        Objects.requireNonNull(storeScope, "storeScope 不可為 null");
    }
}
