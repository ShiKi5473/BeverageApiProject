package tw.niels.beverage_api_project.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfileEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 實作 Spring Security 的 UserDetails 介面 (Record 版本)。
 */
public record AppUserDetails(
        Long userId,
        Long brandId,
        Long storeId,
        String username,
        String password,
        boolean isActive,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails, TenantPrincipal {

    /**
     * 推播用唯一識別（"U:" + userId），取代手機號碼，避免跨品牌同號誤收通知。
     */
    @Override
    public String principalKey() {
        return "U:" + userId;
    }

    // --- 靜態建構方法 (Factory Method) ---
    public static AppUserDetails build(UserEntity user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        Long storeId = null;

        // 檢查是否有員工角色資料
        StaffProfileEntity staffProfile = user.getStaffProfile();
        if (staffProfile != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + staffProfile.getRole().name()));

            if (staffProfile.getStore() != null) {
                storeId = staffProfile.getStore().getId();
            }
        } else {
            // 如果沒有員工資料，預設給予會員角色
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        return new AppUserDetails(
                user.getUserId(),
                user.getBrand().getId(),
                storeId,
                user.getPrimaryPhone(),
                user.getPasswordHash(),
                user.getIsActive(),
                Collections.unmodifiableSet(authorities)
        );
    }

    /**
     * 【新增】由 JWT claims（或 SSE ticket 內容）直接建立身分，不查資料庫。
     * <p>
     * JwtAuthenticationFilter 每個請求都會呼叫，因此不能有 DB 存取；
     * password 為 null（已通過 token 驗證，不再需要密碼），isActive 固定為 true
     * （停權時由 TokenRevocationService 撤銷 token，而非靠每次查 DB 的 isActive）。
     * </p>
     *
     * @param userId   使用者 ID
     * @param brandId  品牌 ID
     * @param storeId  分店 ID（品牌管理員、會員為 null）
     * @param username 帳號（手機號碼，即 JWT subject）
     * @param role     角色（如 ROLE_STAFF），可為 null
     */
    public static AppUserDetails fromClaims(Long userId, Long brandId, Long storeId, String username, String role) {
        Collection<? extends GrantedAuthority> authorities = (role == null || role.isBlank())
                ? Collections.emptySet()
                : Collections.singleton(new SimpleGrantedAuthority(role));
        return new AppUserDetails(userId, brandId, storeId, username, null, true, authorities);
    }

    /**
     * 【新增】取得第一個角色字串（員工／會員只有一個角色），供 SSE ticket 等需要序列化身分的情境使用。
     */
    public String primaryRole() {
        if (authorities == null || authorities.isEmpty()) {
            return null;
        }
        return authorities.iterator().next().getAuthority();
    }

    // --- UserDetails 介面實作 ---
    // Record 自動產生了 username(), password(), authorities()
    // 但介面要求 getUsername(), getPassword()，所以需要轉接

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities();
    }

    @Override
    public String getPassword() {
        return password();
    }

    @Override
    public String getUsername() {
        return username();
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }

    // 這些是預設 true，直接回傳即可
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    // --- 相容性 Getter (Optional) ---
    // 如果您的專案中大量使用了 .getUserId(), .getBrandId()
    // 您可以保留以下方法以免去大幅重構。
    // 如果願意重構，可以刪除這些，改用 record 原生的 .userId()

}