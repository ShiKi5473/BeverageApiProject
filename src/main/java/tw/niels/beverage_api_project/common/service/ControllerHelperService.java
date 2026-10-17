package tw.niels.beverage_api_project.common.service; // 建議放在 common 套件下

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.GuestPrincipal;
import tw.niels.beverage_api_project.security.TenantPrincipal;

import java.util.Optional;

@Service // 標註為 Spring Bean，使其可以被注入
public class ControllerHelperService {

    private final StoreJpaRepository storeRepository; // 新增欄位
    private final UserJpaRepository userRepository; // 新增欄位

    public ControllerHelperService(StoreJpaRepository storeRepository, UserJpaRepository userRepository) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    /**
     * 獲取當前安全的所屬分店實體 (Safe Current Store)
     * 並處理相應的防呆邏輯
     */
    public StoreEntity getSafeCurrentUserStore() {
        Long brandId = getCurrentBrandId();
        Long userId = getCurrentUserId();

        UserEntity user = userRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到當前登入的使用者資料"));

        if (user.getStaffProfile() == null) {
            throw new BadRequestException("非員工帳號或員工檔案缺失，無法執行與分店相關的操作");
        }

        StoreEntity store = user.getStaffProfile().getStore();
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
     * 取得當前的多租戶身分（員工／會員為 AppUserDetails，訪客為 GuestPrincipal）。
     * 供同時開放給訪客與會員的 API（如線上點餐）使用。
     *
     * @return 當前 TenantPrincipal
     */
    public TenantPrincipal getCurrentTenantPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal;
        }
        throw new ResourceNotFoundException("無法獲取當前使用者認證資訊");
    }

    /**
     * 若當前為訪客則回傳 GuestPrincipal，否則回傳 empty。
     */
    public Optional<GuestPrincipal> getCurrentGuestPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GuestPrincipal guest) {
            return Optional.of(guest);
        }
        return Optional.empty();
    }

    /**
     * 獲取當前使用者所屬的品牌 ID（員工、會員、訪客皆適用）
     *
     * @return 品牌 ID (Long)
     */
    public Long getCurrentBrandId() {
        // 改用 TenantPrincipal，讓訪客也能取得品牌（線上點餐唯讀 API 需要）
        return getCurrentTenantPrincipal().brandId();
    }

    /**
     * 獲取當前使用者本身的 UserEntity ID
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

    /**
     * 取得當前員工「可存取的分店 ID」（新增，M-1 分店資料隔離）。
     * <p>
     * 供寫入型 API 放進 Command，由 Handler 在交易內載入資料後比對（例如訂單的 storeId）：
     * <ul>
     *   <li>已綁定分店的員工（STAFF / MANAGER / TESTER，或綁店的品牌管理員）→ 回傳綁定的分店 ID</li>
     *   <li>未綁定分店的品牌管理員（或平台管理員）→ 回傳 null，代表可存取品牌內所有分店</li>
     *   <li>未綁定分店的非管理員帳號（如會員、資料異常的員工）、或非員工身分（訪客）→ 403</li>
     * </ul>
     * </p>
     *
     * @return 綁定的分店 ID；null 代表全品牌
     * @throws AccessDeniedException 非管理員卻未綁定分店，或不是員工 / 會員身分（GlobalExceptionHandler 轉為 403）
     */
    public Long getAccessibleStoreId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 訪客（GuestPrincipal）等非 AppUserDetails 身分沒有分店權限，直接 403（原本 getCurrentUserDetails 會回 404）
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails userDetails)) {
            throw new AccessDeniedException("非員工帳號，無法存取分店資料");
        }

        // 情況 1: 「已綁定店家」的員工 → 只能存取自己的分店
        if (userDetails.storeId() != null) {
            return userDetails.storeId();
        }

        // 情況 2: 「沒有綁定店家」(storeId == null)
        // 必須確保他真的是「品牌管理員」或「平台管理員」，而不是資料異常的員工
        boolean isAdmin = userDetails.getAuthorities() != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BRAND_ADMIN".equals(a.getAuthority()) ||
                        "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            // 修改：權限不足改回 403（原本為 BadRequestException 400）
            throw new AccessDeniedException("帳號權限異常：非管理員帳號必須綁定店家。");
        }
        // 品牌管理員 → 全品牌（後續交由 BrandId 進行資料隔離）
        return null;
    }

    /**
     * 驗證當前員工可以存取指定分店。
     * <p>
     * 修改（M-1 / 存取拒絕改 403）：
     * <ul>
     *   <li>綁店員工存取其他分店：原本拋 BadRequestException（400），改為 Spring Security 的
     *       AccessDeniedException，由 GlobalExceptionHandler 回 403 與標準 ErrorResponse</li>
     *   <li>非管理員卻未綁定分店：同樣改回 403</li>
     *   <li>品牌管理員存取不屬於本品牌（或不存在）的分店：維持 404，不透露其他品牌的分店是否存在</li>
     * </ul>
     * 角色判斷與 {@link #getAccessibleStoreId()} 共用，避免兩處規則不一致。
     * </p>
     *
     * @param targetStoreId 目標分店 ID
     * @throws AccessDeniedException     無權限存取（403）
     * @throws ResourceNotFoundException 品牌管理員指定的分店不屬於本品牌（404）
     */
    public void validateStoreAccess(Long targetStoreId) {
        Long accessibleStoreId = getAccessibleStoreId();

        // 情況 1: 「已綁定店家」的員工 (STAFF, MANAGER, TESTER)
        if (accessibleStoreId != null) {
            if (!accessibleStoreId.equals(targetStoreId)) {
                throw new AccessDeniedException("無權限存取該店家的資料 (ID: " + targetStoreId + ")");
            }
            // 通過檢查
            return;
        }

        // 情況 2: 品牌管理員 → 分店必須屬於本品牌
        Long currentBrandId = getCurrentBrandId();
        boolean isStoreBelongsToBrand = targetStoreId != null
                && storeRepository.findByBrand_IdAndId(currentBrandId, targetStoreId).isPresent();

        if (!isStoreBelongsToBrand) {
            throw new ResourceNotFoundException("找不到該分店，或您無權限存取 (ID: " + targetStoreId + ")");
        }

        // 品牌管理員 -> 允許通過 (後續交由 BrandId 進行資料隔離)
    }
}