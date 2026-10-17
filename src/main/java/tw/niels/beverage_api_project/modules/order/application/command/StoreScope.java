package tw.niels.beverage_api_project.modules.order.application.command;

import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/**
 * 呼叫者可操作的分店範圍（新增，M-1 分店資料隔離）。
 *
 * 新增原因：PATCH /orders/{id}/status、PUT /orders/{id}、PATCH /orders/{id}/checkout、
 * PATCH /orders/{id}/accept 原本只依「品牌」載入訂單，綁定分店 1 的 STAFF / MANAGER / TESTER
 * 可以取消、結案、結帳或修改同品牌分店 2 的訂單。
 * 現在由 Controller 依 token 建立 StoreScope 放進 Command，Handler 在「交易內、載入訂單後」
 * 呼叫 {@link #verifyAccess(Long)} 統一檢查；Command 的 compact constructor 要求 StoreScope 不可為 null，
 * 因此新增的呼叫端無法「忘記帶」而意外取得全品牌權限。
 *
 * @param storeId 可操作的分店 ID；null 代表全品牌（未綁定分店的品牌管理員）
 */
public record StoreScope(Long storeId) {

    /** 全品牌範圍（未綁定分店的品牌管理員） */
    public static StoreScope brandWide() {
        return new StoreScope(null);
    }

    /**
     * 單一分店範圍（綁定分店的員工）。
     *
     * @param storeId 綁定的分店 ID（不可為 null）
     */
    public static StoreScope ofStore(Long storeId) {
        return new StoreScope(Objects.requireNonNull(storeId, "storeId 不可為 null"));
    }

    /**
     * 由「可存取分店 ID」建立範圍：null → 全品牌、非 null → 單一分店。
     * 供 Controller 搭配 ControllerHelperService.getAccessibleStoreId() 使用
     * （該方法已確認 null 只會出現在品牌管理員身上，非管理員未綁店會直接 403）。
     */
    public static StoreScope ofNullable(Long accessibleStoreId) {
        return accessibleStoreId == null ? brandWide() : ofStore(accessibleStoreId);
    }

    /** 是否為全品牌範圍 */
    public boolean isBrandWide() {
        return storeId == null;
    }

    /**
     * 驗證目標分店在可操作範圍內。
     *
     * @param targetStoreId 目標資料（如訂單）所屬的分店 ID
     * @throws AccessDeniedException 分店不符（GlobalExceptionHandler 轉為 403）
     */
    public void verifyAccess(Long targetStoreId) {
        if (!isBrandWide() && !storeId.equals(targetStoreId)) {
            throw new AccessDeniedException("無權限操作其他分店的訂單 (分店 ID: " + targetStoreId + ")");
        }
    }
}
