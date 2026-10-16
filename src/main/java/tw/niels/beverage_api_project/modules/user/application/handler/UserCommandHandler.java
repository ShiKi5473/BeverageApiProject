package tw.niels.beverage_api_project.modules.user.application.handler;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.user.application.command.*;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

/**
 * 員工相關命令處理器
 *
 * 處理員工註冊與資料更新等寫入操作。
 *
 * 處理流程（以 RegisterStaff 為例）：
 * 1. 接收 RegisterStaffCommand
 * 2. 委派 UserService 建立使用者
 * 3. 回傳新員工 ID
 *
 * 處理流程（以 UpdateStaff 為例）：
 * 1. 接收 UpdateStaffCommand（含 brandId、staffId、更新資料）
 * 2. 委派 UserService 更新員工資料
 * 3. 回傳更新後的 StaffDto
 */
@Component
public class UserCommandHandler {

    private final UserService userService;
    private final ControllerHelperService helperService;

    public UserCommandHandler(UserService userService, ControllerHelperService helperService) {
        this.userService = userService;
        this.helperService = helperService;
    }

    @Transactional
    public Long handle(RegisterStaffCommand command) {
        // 提權防護（S-1）：只在 UserController -> UserCommandHandler 這條路徑檢查，
        // 不放進 UserService.createUser，因為 PlatformController 建立首位品牌管理員也會用到它。
        authorizeStaffCreation(command.requestDto());
        UserEntity UserEntity = userService.createUser(command.requestDto());
        return UserEntity.getId();
    }

    /**
     * 依操作者角色限制可建立的員工角色與分店，避免 MANAGER 建立 BRAND_ADMIN 等提權行為。
     * <ul>
     *   <li>任何人皆不可透過此入口建立 BRAND_ADMIN（由平台管理員建立）</li>
     *   <li>MANAGER：只能在自己的分店建立 STAFF</li>
     *   <li>BRAND_ADMIN：可建立 MANAGER / STAFF / TESTER（分店歸屬品牌由 UserService 驗證）</li>
     * </ul>
     * 違規時拋出 AccessDeniedException（由全域例外處理器轉為 403）。
     */
    private void authorizeStaffCreation(CreateUserRequestDto request) {
        CreateUserRequestDto.StaffProfileDto staff = request.getStaffProfile();
        if (staff == null) {
            // 未帶員工資料＝建立一般帳號（會員），不涉及員工角色，無提權疑慮
            return;
        }

        StaffRole targetRole = staff.getRole();
        if (targetRole == StaffRole.BRAND_ADMIN) {
            throw new AccessDeniedException("無權限建立品牌管理員");
        }

        AppUserDetails current = helperService.getCurrentUserDetails();
        boolean isBrandAdmin = current.getAuthorities().stream()
                .anyMatch(a -> AuthorityConstants.ROLE_BRAND_ADMIN.equals(a.getAuthority()));
        if (isBrandAdmin) {
            // 品牌管理員：MANAGER / STAFF / TESTER 皆可（BRAND_ADMIN 已於上方排除）
            return;
        }

        // 其餘（MANAGER）：只能建立本店 STAFF
        if (targetRole != StaffRole.STAFF) {
            throw new AccessDeniedException("店長只能建立店員(STAFF)帳號");
        }
        Long ownStoreId = current.storeId();
        if (ownStoreId == null) {
            throw new AccessDeniedException("帳號未綁定分店，無法建立員工");
        }
        if (staff.getStoreId() == null) {
            // 未指定分店時，預設為店長自己的分店
            staff.setStoreId(ownStoreId);
        } else if (!ownStoreId.equals(staff.getStoreId())) {
            throw new AccessDeniedException("店長只能在自己的分店建立員工");
        }
    }

    @Transactional
    public StaffDto handle(UpdateStaffCommand command) {
        return userService.updateStaff(command.brandId(), command.staffId(), command.requestDto());
    }
}
