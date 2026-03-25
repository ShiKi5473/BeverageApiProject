package tw.niels.beverage_api_project.modules.user.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.user.application.query.*;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.util.List;

/**
 * 員工相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收 GetStaffListQuery（含 brandId、storeId）
 * 2. 委派 UserService 查詢員工列表
 * 3. 回傳 List<StaffDto>
 */
@Component
public class UserQueryHandler {

    private final UserService userService;

    public UserQueryHandler(UserService userService) {
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<StaffDto> handle(GetStaffListQuery query) {
        return userService.getStaffList(query.brandId(), query.storeId());
    }
}
