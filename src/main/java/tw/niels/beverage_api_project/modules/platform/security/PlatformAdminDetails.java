package tw.niels.beverage_api_project.modules.platform.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tw.niels.beverage_api_project.modules.platform.entity.PlatformAdmin;

import java.util.Collection;
import java.util.Collections;

public record PlatformAdminDetails(
        Long adminId,
        String username,
        String password,
        GrantedAuthority authority
) implements UserDetails {

    // 靜態工廠方法 (維持原有的 build 邏輯)
    public static PlatformAdminDetails build(PlatformAdmin admin) {
        return new PlatformAdminDetails(
                admin.getAdminId(),
                admin.getUsername(),
                admin.getPasswordHash(),
                new SimpleGrantedAuthority(admin.getRole())
        );
    }

    // --- UserDetails 介面實作 ---
    // 由於 Record 的預設 getter 是 name() (例如 username())，
    // 而 UserDetails 要求 getUsername()，所以必須手動橋接。

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(authority);
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }

    // 下列 Boolean 值依舊回傳 true
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}