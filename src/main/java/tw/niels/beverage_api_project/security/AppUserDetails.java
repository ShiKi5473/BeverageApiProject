package tw.niels.beverage_api_project.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;

/**
 * 實作 Spring Security 的 UserDetails 介面。
 * 這個類別用於將我們的 Staff 實體轉換成 Spring Security 可理解的使用者詳細資訊物件。
 */

public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final Long brandId;
    private final String username; // 使用手機號碼作為 username
    private final String password;
    private final Long storeId;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(Long userId, Long brandId, Long storeId, String username, String password, boolean isActive,
                          Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.brandId = brandId;
        this.username = username;
        this.password = password;
        this.storeId = storeId;
        this.isActive = isActive;
        this.authorities = authorities;
    }

    public static AppUserDetails build(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        Long storeId = null;

        // 檢查是否有員工角色資料
        StaffProfile staffProfile = user.getStaffProfile();
        if (staffProfile != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + staffProfile.getRole().name()));

            if (staffProfile.getStore() != null) {
                storeId = staffProfile.getStore().getStoreId();
            }
        } else {
            // 如果沒有員工資料，預設給予會員角色
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        return new AppUserDetails(
                user.getUserId(),
                user.getBrand().getBrandId(),
                storeId,
                user.getPrimaryPhone(),
                user.getPasswordHash(),
                user.getActive(),
                authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBrandId() {
        return brandId;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public Long getStoreId() {
        return storeId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }

}