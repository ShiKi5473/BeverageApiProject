package tw.niels.beverage_api_project.modules.platform.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tw.niels.beverage_api_project.modules.platform.entity.PlatformAdmin;

import java.util.Collection;
import java.util.Collections;

public class PlatformAdminDetails implements UserDetails {

    @Getter
    private final Long adminId;
    private final String username;
    private final String password;
    private final GrantedAuthority authority;

    public PlatformAdminDetails(Long adminId, String username, String password, String role) {
        this.adminId = adminId;
        this.username = username;
        this.password = password;
        this.authority = new SimpleGrantedAuthority(role);
    }

    public static PlatformAdminDetails build(PlatformAdmin admin) {
        return new PlatformAdminDetails(
                admin.getAdminId(),
                admin.getUsername(),
                admin.getPasswordHash(),
                admin.getRole()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(authority);
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}