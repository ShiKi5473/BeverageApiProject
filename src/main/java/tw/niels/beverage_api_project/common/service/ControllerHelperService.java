package tw.niels.beverage_api_project.common.service; // 建議放在 common 套件下

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service // 標註為 Spring Bean，使其可以被注入
public class ControllerHelperService {

    /**
     * 獲取當前已認證的使用者詳細資訊 (AppUserDetails)
     * 
     * @return AppUserDetails 物件
     */
    public AppUserDetails getCurrentUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AppUserDetails) {
            return (AppUserDetails) principal;
        }

        // 如果 principal 不是 AppUserDetails (例如匿名使用者)，則拋出例外
        throw new ResourceNotFoundException("無法獲取當前使用者認證資訊");
    }

    /**
     * 獲取當前使用者所屬的 Brand ID
     * 
     * @return 品牌 ID (Long)
     */
    public Long getCurrentBrandId() {
        return getCurrentUserDetails().getBrandId();
    }

    /**
     * 獲取當前使用者本身的 User ID
     * 
     * @return 使用者 ID (Long)
     */
    public Long getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }
}