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
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Application - Event Handler
 * ============================================================
 *
 * 訂單完成後會員積點累積事件監聽器
 *
 * 訂閱 {@link OrderClosedEvent}，在訂單事務提交後（AFTER_COMMIT）
 * 開啟獨立新事務，為會員累積本次訂單賺取的積點。
 *
 * 處理流程：
 * 1. 若為訪客訂單（memberId = null）或未賺取積點（pointsEarned = 0），直接跳過
 * 2. 載入會員 Domain Model
 * 3. 呼叫 {@code member.earnPoints()} 累積積點（附帶稽核日誌）
 * 4. 透過 {@link MemberRepository#save(Member)} 持久化積點餘額與交易紀錄
 *
 * 設計決策：
 * - 使用 AFTER_COMMIT + REQUIRES_NEW，確保僅在訂單提交成功後才累積積點
 * - 若會員不存在（資料異常），記錄警告但不拋出例外，避免影響其他下游處理
 * ============================================================
 */
@Component
public class OrderClosedMemberPointHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderClosedMemberPointHandler.class);

    private final MemberRepository memberRepository;

    public OrderClosedMemberPointHandler(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 處理訂單完成事件，為會員累積積點。
     *
     * @param event 訂單完成事件，攜帶 memberId、brandId、pointsEarned
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderClosedEvent event) {
        // 訪客訂單或未賺取積點，直接跳過
        if (event.memberId() == null || event.pointsEarned() == null || event.pointsEarned() <= 0) {
            return;
        }

        // 載入會員 Domain Model
        Member member = memberRepository.findById(event.brandId(), event.memberId()).orElse(null);
        if (member == null) {
            logger.warn("訂單 ID={} 積點累積失敗：找不到會員 ID={}", event.orderId(), event.memberId());
            return;
        }

        // 累積積點（業務規則由 Domain Model 保護）
        String reason = "訂單完成積點：訂單 ID " + event.orderId();
        member.earnPoints(event.pointsEarned(), reason);

        // 持久化（含積點餘額與 MemberPointLog 稽核紀錄）
        memberRepository.save(member);
        logger.info("訂單 ID={} 會員 ID={} 累積積點 {} 點，完成", event.orderId(), event.memberId(), event.pointsEarned());
    }
}
