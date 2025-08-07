package tw.niels.beverage_api_project.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.repository.StaffRepository;

/**
 * 實現 UserDetailsService 介面，用於 Spring Security 載入使用者資訊。
 * 這裡將使用者名稱和品牌ID合併為一個字串來查詢，例如 "username:brandId"。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    // 注入 Staff 實體的資料庫操作介面
    private final StaffRepository staffRepository;

    // 依賴注入的建構函式，Spring 會自動注入 StaffRepository 實例
    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    /**
     * 核心方法，根據傳入的使用者名稱載入使用者詳細資訊。
     * 這裡的使用者名稱是客製化的 "username:brandId" 格式。
     *
     * @param usernameAndBrandId 格式為 "username:brandId" 的使用者名稱字串
     * @return 封裝了使用者詳細資訊的 UserDetails 物件
     * @throws UsernameNotFoundException 如果找不到使用者或格式不正確
     */
    @Override
    public UserDetails loadUserByUsername(String usernameAndBrandId) throws UsernameNotFoundException {
        // 根據 ':' 分隔符號將字串拆分成兩部分：使用者名稱和品牌ID
        String[] parts = usernameAndBrandId.split(":");
        
        // 檢查分割後的部分是否剛好為兩部分，否則拋出異常
        if (parts.length != 2) {
            throw new UsernameNotFoundException("傳入的使用者名稱格式不正確，應為 'username:brandId'");
        }

        // 取得使用者名稱部分
        String username = parts[0];
        Long brandId;
        
        try {
            // 嘗試將品牌ID字串轉換為長整數（Long）
            brandId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            // 如果轉換失敗，表示品牌ID格式不正確，拋出異常
            throw new UsernameNotFoundException("傳入的 brandId 格式不正確");
        }

        // 使用 StaffRepository 查詢資料庫，根據品牌ID和使用者名稱來尋找員工
        // findByBrand_BrandIdAndUsername() 是自定義的 Spring Data JPA 方法
        Staff staff = staffRepository.findByBrand_BrandIdAndUsername(brandId, username)
            // 如果查詢結果為空（找不到該員工），則拋出異常
            .orElseThrow(() -> new UsernameNotFoundException("在品牌 " + brandId + " 中找不到使用者: " + username));

        // 如果找到員工，將其封裝成 Spring Security 所需的 UserDetails 物件並返回
        return new StaffUserDetails(staff);
    }
}