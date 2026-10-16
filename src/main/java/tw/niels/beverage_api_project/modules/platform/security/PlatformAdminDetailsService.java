package tw.niels.beverage_api_project.modules.platform.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.platform.repository.PlatformAdminJpaRepository;

@Service("platformAdminDetailsService") // 命名 Bean 以便區分
public class PlatformAdminDetailsService implements UserDetailsService {

    // 【修正】批次改名殘留：欄位名稱與型別同名，改為小寫開頭的變數名稱
    private final PlatformAdminJpaRepository platformAdminJpaRepository;

    public PlatformAdminDetailsService(PlatformAdminJpaRepository platformAdminJpaRepository) {
        this.platformAdminJpaRepository = platformAdminJpaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 平台管理員登入，不檢查 brandId
        return platformAdminJpaRepository.findByUsername(username)
                .map(PlatformAdminDetails::build)
                .orElseThrow(() -> new UsernameNotFoundException("Platform admin not found with username: " + username));
    }
}