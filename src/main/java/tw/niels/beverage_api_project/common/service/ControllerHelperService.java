package tw.niels.beverage_api_project.common.service; // 建議放在 common 套件下

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service // 標註為 Spring Bean，使其可以被注入
public class ControllerHelperService {

    private final StoreRepository storeRepository; // 新增欄位
    private final UserRepository userRepository; // 新增欄位

    public ControllerHelperService(StoreRepository storeRepository, UserRepository userRepository) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    /**
     * 獲取當前安全的所屬分店實體 (Safe Current Store)
     * 並處理相應的防呆邏輯
     */
    public Store getSafeCurrentUserStore() {
        Long brandId = getCurrentBrandId();
        Long userId = getCurrentUserId();

        User user = userRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到當前登入的使用者資料"));

        if (user.getStaffProfile() == null) {
            throw new BadRequestException("非員工帳號或員工檔案缺失，無法執行與分店相關的操作");
        }

        Store store = user.getStaffProfile().getStore();
        if (store == null) {
            throw new BadRequestException("當前使用者不屬於任何分店，無法執行與分店相關的操作");
        }
        return store;
    }

    /**
     * 獲取當前已認證的使用者詳細資訊 (AppUserDetails)
     * 
     * @return AppUserDetails 物件
     */
    public AppUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails) {
            return (AppUserDetails) authentication.getPrincipal();
        }

        throw new ResourceNotFoundException("無法獲取當前使用者認證資訊");
    }

    /**
     * 獲取當前使用者所屬的 Brand ID
     * 
     * @return 品牌 ID (Long)
     */
    public Long getCurrentBrandId() {
        return getCurrentUserDetails().brandId();
    }

    /**
     * 獲取當前使用者本身的 User ID
     *
     * @return 使用者 ID (Long)
     */
    public Long getCurrentUserId() {
        return getCurrentUserDetails().userId();
    }

    /**
     * 獲取當前使用者所屬的 Store ID (如果有的話)
     * * @return 店家 ID (Long)，可能為 null
     */
    public Long getCurrentStoreId() {
        return getCurrentUserDetails().storeId();
    }

    public void validateStoreAccess(Long targetStoreId) {
        AppUserDetails userDetails = getCurrentUserDetails();
        Long currentUserStoreId = userDetails.storeId();
        Long currentBrandId = userDetails.brandId();

        // 情況 1: 如果是「已綁定店家」的員工 (STAFF, MANAGER)
        if (currentUserStoreId != null) {
            if (!currentUserStoreId.equals(targetStoreId)) {
                throw new BadRequestException("無權限存取該店家的資料 (ID: " + targetStoreId + ")");
            }
            // 通過檢查
            return;
        }

        // 情況 2: 如果「沒有綁定店家」 (storeId == null)
        // 我們必須確保他真的是「品牌管理員」或「平台管理員」，而不是資料異常的員工
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BRAND_ADMIN") ||
                        a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        if (!isAdmin) {
            // 如果 storeId 是 null，但他又不是管理員 -> 視為異常或權限不足
            throw new BadRequestException("帳號權限異常：非管理員帳號必須綁定店家。");
        }

        boolean isStoreBelongsToBrand = storeRepository.findByBrand_IdAndId(currentBrandId, targetStoreId).isPresent();

        if (!isStoreBelongsToBrand) {
            throw new ResourceNotFoundException("找不到該分店，或您無權限存取 (ID: " + targetStoreId + ")");
        }

        // 品牌管理員 -> 允許通過 (後續交由 BrandId 進行資料隔離)
    }
}