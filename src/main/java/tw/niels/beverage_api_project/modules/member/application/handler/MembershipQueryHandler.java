package tw.niels.beverage_api_project.modules.member.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.member.application.query.*;
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.util.Optional;

/**
 * 會員相關查詢處理器（唯讀）
 *
 * 處理流程：
 * 1. 接收 GetMemberByPhoneQuery（含手機號碼與 brandId）
 * 2. 委派 UserService 依手機號碼查詢會員
 * 3. 回傳 Optional<MemberDto>
 */
@Component
public class MembershipQueryHandler {

    private final UserService userService;

    public MembershipQueryHandler(UserService userService) {
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public Optional<MemberDto> handle(GetMemberByPhoneQuery query) {
        return userService.findMemberByPhone(query.phone(), query.brandId());
    }
}
