package tw.niels.beverage_api_project.modules.platform.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.platform.repository.PlatformAdminJpaRepository;

@Service("platformAdminDetailsService") // 命名 Bean 以便區分
public class PlatformAdminDetailsService implements UserDetailsService {

    private final PlatformAdminJpaRepository PlatformAdminJpaRepository;

    public PlatformAdminDetailsService(PlatformAdminJpaRepository PlatformAdminJpaRepository) {
        this.PlatformAdminJpaRepository = PlatformAdminJpaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 平台管理員登入，不檢查 brandId
        return PlatformAdminJpaRepository.findByUsername(username)
                .map(PlatformAdminDetails::build)
                .orElseThrow(() -> new UsernameNotFoundException("Platform admin not found with username: " + username));
    }
}