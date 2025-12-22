package tw.niels.beverage_api_project.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;

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
) implements UserDetails {

    // --- 靜態建構方法 (Factory Method) ---
    public static AppUserDetails build(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        Long storeId = null;

        // 檢查是否有員工角色資料
        StaffProfile staffProfile = user.getStaffProfile();
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