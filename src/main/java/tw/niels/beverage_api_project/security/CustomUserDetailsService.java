package tw.niels.beverage_api_project.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

/**
 * 實現 UserDetailsService 介面，用於 Spring Security 載入使用者資訊。
 * 這裡將使用者名稱和品牌ID合併為一個字串來查詢，例如 "username:brandId"。
 */

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional // 加上交易註解以確保能懶加載 (Lazy Loading) 關聯的 Profile
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 從 ThreadLocal 取得由 AuthService 設定的 brandId
        Long brandId = BrandContextHolder.getBrandId();

        if (brandId == null) {
            // 這是一個重要的安全檢查，防止在沒有品牌上下文的情況下進行認證
            throw new IllegalStateException("BrandId not found in security context.");
        }

        // 在我們的設計中，username 即為 primaryPhone
        // 使用包含 brandId 的查詢方法，確保租戶隔離
        User user = userRepository.findByPrimaryPhoneAndBrandId(username, brandId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with phone: " + username + " for brand: " + brandId));

        return AppUserDetails.build(user);
    }
}