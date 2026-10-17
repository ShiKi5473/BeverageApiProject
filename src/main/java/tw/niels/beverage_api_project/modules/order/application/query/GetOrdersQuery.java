package tw.niels.beverage_api_project.modules.order.application.query;

import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;

import java.time.Instant;

/**
 * 取得訂單列表查詢
 *
 * 修改：新增下單時間區間 from / to（皆可為 null，預設最近 24 小時），
 * 避免一次載入整店歷史訂單；查詢端另有筆數上限。
 *
 * @param brandId 品牌 ID
 * @param storeId 分店 ID
 * @param status  訂單狀態（可為 null，代表不篩選）
 * @param from    下單時間起（含，可為 null）
 * @param to      下單時間迄（不含，可為 null）
 */
public record GetOrdersQuery(
    Long brandId,
    Long storeId,
    OrderStatus status,
    Instant from,
    Instant to
) {}
