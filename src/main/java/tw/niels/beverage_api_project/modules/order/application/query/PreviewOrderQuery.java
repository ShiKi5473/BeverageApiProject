package tw.niels.beverage_api_project.modules.order.application.query;

import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;

import java.util.List;

/**
 * 訂單金額試算查詢（新增，不寫入資料庫）。
 *
 * @param brandId     品牌 ID
 * @param storeId     分店 ID（可為 null；有值時驗證分店屬於該品牌）
 * @param items       品項
 * @param memberId    會員 ID（可為 null）
 * @param pointsToUse 欲使用點數（可為 null，視為 0）
 */
public record PreviewOrderQuery(
    Long brandId,
    Long storeId,
    List<OrderItemDto> items,
    Long memberId,
    Long pointsToUse
) {}
