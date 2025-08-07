package tw.niels.beverage_api_project.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import tw.niels.beverage_api_project.modules.user.entity.Staff;

/**
 * 實作 Spring Security 的 UserDetails 介面。
 * 這個類別用於將我們的 Staff 實體轉換成 Spring Security 可理解的使用者詳細資訊物件。
 */
public class StaffUserDetails implements UserDetails {
    // 儲存原始的 Staff 實體物件
    private final Staff staff;

    // 建構函式：接受一個 Staff 實體來初始化 StaffUserDetails
    public StaffUserDetails(Staff staff) {
        this.staff = staff;
    }

    /**
     * 返回授予使用者的權限集合。
     * 這裡我們將 Staff 實體中的角色（Role）轉換為 Spring Security 的 GrantedAuthority 物件。
     * 例如，如果 Staff 的角色是 "ADMIN"，這裡會返回 "ROLE_ADMIN"。
     * @return 包含使用者權限的集合。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + staff.getRole().name()));
    }

    /**
     * 返回用於認證的密碼。
     * 這裡我們返回 Staff 實體中儲存的密碼雜湊值（passwordHash）。
     * @return 密碼雜湊值。
     */
    @Override
    public String getPassword() {
        return staff.getPasswordHash();
    }

    /**
     * 返回用於認證的使用者名稱。
     * 這裡我們使用 Staff 實體中的 username 作為使用者名稱。
     * @return 使用者名稱。
     */
    @Override
    public String getUsername() {
        return staff.getUsername();
    }

    /**
     * 檢查帳號是否未過期。
     * 這裡預設返回 true，表示帳號永不過期。
     * 在實際應用中，可以根據 Staff 實體的欄位來判斷。
     * @return 帳號是否未過期。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 檢查帳號是否未被鎖定。
     * 這裡預設返回 true，表示帳號永不鎖定。
     * 在實際應用中，可以根據 Staff 實體的狀態來判斷。
     * @return 帳號是否未被鎖定。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 檢查憑證（密碼）是否未過期。
     * 這裡預設返回 true，表示密碼永不過期。
     * 在實際應用中，可以根據密碼更新日期來判斷。
     * @return 憑證是否未過期。
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 檢查帳號是否啟用。
     * 這裡我們根據 Staff 實體的 isActive 狀態來判斷帳號是否可用。
     * @return 帳號是否啟用。
     */
    @Override
    public boolean isEnabled() {
        return staff.isActive();
    }

    // 提供一個公共方法，讓外部程式碼能夠方便地獲取原始的 Staff 物件
    public Staff getStaff() {
        return staff;
    }
}