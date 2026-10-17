package tw.niels.beverage_api_project.modules.order.application.command;

import java.util.Objects;

/**
 * 處理訂單付款命令
 *
 * 修改說明（M-1 分店資料隔離）：新增 storeScope（呼叫者可操作的分店範圍），
 * ProcessPaymentHandler 載入訂單後驗證訂單所屬分店，綁店員工不可結帳其他分店的訂單（403）。
 *
 * @param brandId       品牌 ID
 * @param orderId       訂單 ID
 * @param memberId      會員 ID（可為 null）
 * @param pointsToUse   欲使用點數（可為 null）
 * @param paymentMethod 付款方式代碼
 * @param storeScope    呼叫者可操作的分店範圍（不可為 null；全品牌請用 StoreScope.brandWide()）
 */
public record ProcessPaymentCommand(
    Long brandId,
    Long orderId,
    Long memberId,
    Long pointsToUse,
    String paymentMethod,
    StoreScope storeScope
) {
    /** 新增：強制帶入分店範圍，避免呼叫端遺漏而跳過分店檢查 */
    public ProcessPaymentCommand {
        Objects.requireNonNull(storeScope, "storeScope 不可為 null");
    }
}
