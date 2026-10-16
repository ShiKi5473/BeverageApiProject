package tw.niels.beverage_api_project.security;

/**
 * 多租戶身分的共同介面。
 * <p>
 * 由「品牌員工／會員」({@link AppUserDetails}) 與「訪客」({@link GuestPrincipal}) 共同實作，
 * 讓 Controller / Service 可以不分身分類型取得所屬品牌，並取得推播用的唯一識別。
 * </p>
 */
public interface TenantPrincipal {

    /** 所屬品牌 ID（多租戶隔離用） */
    Long brandId();

    /**
     * 推播與 STOMP user destination 使用的唯一識別鍵。
     * 員工／會員為 {@code "U:" + userId}，訪客為 {@code "GUEST:" + uuid}。
     * 不使用手機號碼，避免跨品牌同號互相收到通知。
     */
    String principalKey();
}
