package tw.niels.beverage_api_project.common.service; // 建議放在 common 套件下

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service // 標註為 Spring Bean，使其可以被注入
public class ControllerHelperService {

    private final StoreRepository storeRepository; // 新增欄位

    public ControllerHelperService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
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

    /**
     * 獲取當前使用者所屬的 Store ID (如果有的話)
     * * @return 店家 ID (Long)，可能為 null
     */
    public Long getCurrentStoreId() {
        return getCurrentUserDetails().getStoreId();
    }

    public void validateStoreAccess(Long targetStoreId) {
        AppUserDetails userDetails = getCurrentUserDetails();
        Long currentUserStoreId = userDetails.getStoreId();
        Long currentBrandId = userDetails.getBrandId();

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