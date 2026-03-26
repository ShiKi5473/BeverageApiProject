package tw.niels.beverage_api_project.modules.member.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.modules.member.application.command.*;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

/**
 * 會員相關命令處理器
 *
 * 處理會員註冊與積點調整等寫入操作。
 *
 * 處理流程（以 RegisterMember 為例）：
 * 1. 接收 RegisterMemberCommand
 * 2. 委派 UserService 建立使用者（過渡期）
 * 3. 回傳新會員 ID
 *
 * 處理流程（以 AdjustPoints 為例）：
 * 1. 接收 AdjustPointsCommand（含 brandId、memberId、points、reason）
 * 2. 委派 MemberPointService 執行積點調整
 */
@Component
public class MembershipCommandHandler {

    private final UserService userService;
    private final MemberPointService pointService;

    public MembershipCommandHandler(UserService userService, MemberPointService pointService) {
        this.userService = userService;
        this.pointService = pointService;
    }

    @Transactional
    public Long handle(RegisterMemberCommand command) {
        UserEntity newUser = userService.createUser(command.requestDto());
        return newUser.getId();
    }

    @Transactional
    public void handle(AdjustPointsCommand command) {
        pointService.adjustPoints(command.brandId(), command.memberId(), command.points(), command.reason(), command.reasonType());
    }
}
