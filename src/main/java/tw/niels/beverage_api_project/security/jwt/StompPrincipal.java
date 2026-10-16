package tw.niels.beverage_api_project.security.jwt;

import java.security.Principal;

import tw.niels.beverage_api_project.security.TenantPrincipal;

/**
 * 【新增】STOMP 連線（WebSocket session）使用的 Principal。
 * <p>
 * Spring 的 user destination（/user/queue/orders）以 {@link Principal#getName()} 路由。
 * 原本使用 UsernamePasswordAuthenticationToken，getName() 會回傳手機號碼 → 跨品牌同號會收到彼此通知。
 * 改為固定回傳 {@link TenantPrincipal#principalKey()}（員工／會員 "U:"+userId，訪客 "GUEST:"+uuid），
 * 與 OrderNotificationService 的 convertAndSendToUser 收件者一致。
 * </p>
 *
 * @param name   推播識別（principalKey）
 * @param tenant 原始身分（AppUserDetails 或 GuestPrincipal），供需要時取得 brandId 等資訊
 */
public record StompPrincipal(String name, TenantPrincipal tenant) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
