package tw.niels.beverage_api_project.modules.user.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.user.application.command.*;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
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

    public UserCommandHandler(UserService userService) {
        this.userService = userService;
    }

    @Transactional
    public Long handle(RegisterStaffCommand command) {
        User user = userService.createUser(command.requestDto());
        return user.getId();
    }

    @Transactional
    public StaffDto handle(UpdateStaffCommand command) {
        return userService.updateStaff(command.brandId(), command.staffId(), command.requestDto());
    }
}
