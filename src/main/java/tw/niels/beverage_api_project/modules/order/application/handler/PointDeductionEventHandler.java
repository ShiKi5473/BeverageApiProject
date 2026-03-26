package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tw.niels.beverage_api_project.modules.member.service.MemberPointService;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderPointsDeductedEvent;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

/**
 * 處理積點扣減的事件監聽器（處理 C3 技術債）
 *
 * 將原先在結帳事務內同步執行的積點扣減移至 AFTER_COMMIT。
 * 收到 OrderPointsDeductedEvent 後，開啟新事務扣減積點，
 * 確保只有當訂單事務成功提交後，才會真正執行扣減。
 */
@Component
public class PointDeductionEventHandler {

    private final MemberPointService memberPointService;
    private final UserService userService;

    public PointDeductionEventHandler(MemberPointService memberPointService, UserService userService) {
        this.memberPointService = memberPointService;
        this.userService = userService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderPointsDeductedEvent event) {
        if (event.memberId() != null && event.pointsUsed() > 0) {
            UserEntity memberUser = userService.getMember(event.brandId(), event.memberId());
            // TODO: 因應舊有 MemberPointService 邏輯，目前 OrderEntity 參數傳入 null，待 Phase 3 重構
            memberPointService.usePoints(memberUser, null, event.pointsUsed());
        }
    }
}
