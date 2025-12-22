package tw.niels.beverage_api_project.common.controller;

import org.springframework.security.core.context.SecurityContextHolder;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.security.AppUserDetails;

public abstract class BaseController {

    /**
     * 獲取當前已認證的使用者詳細資訊 (AppUserDetails)
     * * @return AppUserDetails 物件
     * 
     * @throws ResourceNotFoundException 如果找不到認證資訊
     */
    protected AppUserDetails getCurrentUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AppUserDetails) {
            return (AppUserDetails) principal;
        }

        // 如果 principal 不是 AppUserDetails (例如匿名使用者)，則拋出例外
        // 這裡也可以考慮拋出 401 或 500 相關的例外
        throw new ResourceNotFoundException("無法獲取當前使用者認證資訊");
    }

    /**
     * 獲取當前使用者所屬的 Brand ID
     * * @return 品牌 ID (Long)
     */
    protected Long getCurrentBrandId() {
        return getCurrentUserDetails().brandId();
    }

    /**
     * 獲取當前使用者本身的 User ID
     * * @return 使用者 ID (Long)
     */
    protected Long getCurrentUserId() {
        return getCurrentUserDetails().userId();
    }
}