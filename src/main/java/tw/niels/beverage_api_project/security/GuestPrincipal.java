package tw.niels.beverage_api_project.security;

import java.security.Principal;

/**
 * 訪客（線上點餐未登入會員）的認證主體。
 * <p>
 * 由 GUEST 類型的 JWT 建立，不對應資料庫中的使用者，
 * 僅授予 ROLE_GUEST，可存取線上點餐所需的唯讀商品／分店 API 與建立線上訂單。
 * </p>
 *
 * @param guestId     訪客唯一識別，格式為 {@code "GUEST:" + uuid}（即 JWT subject）
 * @param brandId     訪客點餐的品牌 ID（登入時指定並寫入 token）
 * @param displayName 訪客輸入的暱稱
 */
public record GuestPrincipal(String guestId, Long brandId, String displayName)
        implements TenantPrincipal, Principal {

    /** 訪客角色名稱（Spring Security authority） */
    public static final String ROLE_GUEST = "ROLE_GUEST";

    @Override
    public String principalKey() {
        // 訪客的 subject 本身即以 "GUEST:" 開頭，可直接作為推播識別
        return guestId;
    }

    @Override
    public String getName() {
        // STOMP user destination 以 Principal#getName 路由，統一使用 principalKey
        return principalKey();
    }
}
