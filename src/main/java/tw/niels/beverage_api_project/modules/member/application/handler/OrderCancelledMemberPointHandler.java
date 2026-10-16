package tw.niels.beverage_api_project.modules.member.application.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderCancelledEvent;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Application - Event Handler
 * ============================================================
 *
 * 訂單取消後會員積點退還事件監聽器
 *
 * 訂閱 {@link OrderCancelledEvent}，在訂單取消事務提交後（AFTER_COMMIT）
 * 開啟獨立新事務，將原訂單使用的積點退還給會員。
 *
 * 處理流程：
 * 1. 若為訪客訂單（memberId = null）或未使用積點（pointsUsed = 0），直接跳過
 * 2. 載入會員 Domain Model
 * 3. 呼叫 {@code member.refundPoints()} 退還積點（附帶稽核日誌）
 * 4. 透過 {@link MemberRepository#save(Member)} 持久化積點餘額與交易紀錄
 *
 * 設計決策：
 * - 使用 AFTER_COMMIT + REQUIRES_NEW，確保僅在取消事務成功後才退還積點
 * - 若會員不存在（資料異常），記錄錯誤日誌但不拋出例外
 * ============================================================
 */
@Component
public class OrderCancelledMemberPointHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancelledMemberPointHandler.class);

    private final MemberRepository memberRepository;

    public OrderCancelledMemberPointHandler(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 處理訂單取消事件，退還會員已使用的積點。
     *
     * @param event 訂單取消事件，攜帶 memberId、brandId、pointsUsed
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCancelledEvent event) {
        // 訪客訂單或未使用積點，無需退還
        if (event.memberId() == null || event.pointsUsed() == null || event.pointsUsed() <= 0) {
            return;
        }

        // 載入會員 Domain Model
        Member member = memberRepository.findById(event.brandId(), event.memberId()).orElse(null);
        if (member == null) {
            logger.error("訂單 ID={} 積點退還失敗：找不到會員 ID={}", event.orderId(), event.memberId());
            return;
        }

        // 退還積點（業務規則由 Domain Model 保護）
        String reason = "訂單取消退點：訂單 ID " + event.orderId();
        member.refundPoints(event.pointsUsed(), reason);

        // 持久化（含積點餘額與 MemberPointLog 稽核紀錄）
        memberRepository.save(member);
        logger.info("訂單 ID={} 會員 ID={} 退還積點 {} 點，完成", event.orderId(), event.memberId(), event.pointsUsed());
    }
}
