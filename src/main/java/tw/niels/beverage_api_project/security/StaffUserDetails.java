package tw.niels.beverage_api_project.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import tw.niels.beverage_api_project.modules.user.entity.Staff;

public class StaffUserDetails implements UserDetails{
    private final Staff staff;

    public StaffUserDetails(Staff staff){
        this.staff = staff;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + staff.getRole().name()));
    }
    @Override
    public String getPassword(){
        return staff.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return staff.getUsername();
    }

       @Override
    public boolean isAccountNonExpired() {
        // 帳號是否未過期，在此專案中我們預設為 true
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 帳號是否未被鎖定，在此專案中我們預設為 true
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // 憑證 (密碼) 是否未過期，在此專案中我們預設為 true
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 帳號是否啟用
        return staff.isActive();
    }

    // 提供一個方便的方法來獲取原始的 Staff 物件
    public Staff getStaff() {
        return staff;
    }


}
