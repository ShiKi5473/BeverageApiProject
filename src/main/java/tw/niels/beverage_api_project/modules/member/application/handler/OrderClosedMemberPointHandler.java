package tw.niels.beverage_api_project.modules.member.application.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
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
 * - 與「結帳時同步扣點」的關係（CLAUDE.md §5.3）：扣點在結帳交易內完成，累點才是 AFTER_COMMIT
 * - 併發保護：MemberProfileEntity 的 @Version 樂觀鎖。累點可能與同一會員的另一筆結帳扣點同時發生，
 *   撞到版本衝突時重試（最多 3 次），仍失敗只記錄錯誤，不讓例外影響已提交的訂單狀態變更
 * ============================================================
 */
@Component
public class OrderClosedMemberPointHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderClosedMemberPointHandler.class);

    /** 樂觀鎖衝突時的最大嘗試次數 */
    private static final int MAX_ATTEMPTS = 3;

    private final MemberRepository memberRepository;
    // 每次嘗試都開一個全新的交易（REQUIRES_NEW）：
    // 樂觀鎖衝突是在 commit 時才會被偵測到，必須由交易範圍「外側」捕捉，才能重新載入最新版本再試一次。
    // （原本用 @Transactional(REQUIRES_NEW) 標在監聽器上，衝突例外只能往外丟、無法在這裡重試）
    private final TransactionTemplate requiresNewTx;

    public OrderClosedMemberPointHandler(MemberRepository memberRepository,
                                         PlatformTransactionManager transactionManager) {
        this.memberRepository = memberRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 處理訂單完成事件，為會員累積積點。
     *
     * @param event 訂單完成事件，攜帶 memberId、brandId、pointsEarned
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderClosedEvent event) {
        // 訪客訂單或未賺取積點，直接跳過
        if (event.memberId() == null || event.pointsEarned() == null || event.pointsEarned() <= 0) {
            return;
        }

        // 修正：AFTER_COMMIT 監聽器裡丟出的例外會冒泡到「已成功提交」的訂單狀態變更請求
        // （客人看到錯誤，但訂單其實已完成）。改成：樂觀鎖衝突就重試；其他錯誤只記錄，絕不往外丟。
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                requiresNewTx.executeWithoutResult(status -> earnPoints(event));
                return;
            } catch (OptimisticLockingFailureException e) {
                logger.warn("訂單 ID={} 會員 ID={} 累點遇到併發衝突，第 {}/{} 次嘗試",
                        event.orderId(), event.memberId(), attempt, MAX_ATTEMPTS);
            } catch (RuntimeException e) {
                // 例如停用的會員無法累點（DomainException）：不重試，記錄後結束
                logger.error("訂單 ID={} 會員 ID={} 積點累積失敗：{}",
                        event.orderId(), event.memberId(), e.getMessage(), e);
                return;
            }
        }
        logger.error("訂單 ID={} 會員 ID={} 積點累積重試 {} 次仍失敗，需人工補點（pointsEarned={}）",
                event.orderId(), event.memberId(), MAX_ATTEMPTS, event.pointsEarned());
    }

    /** 單次嘗試：載入會員、累點、儲存（在 REQUIRES_NEW 交易內執行） */
    private void earnPoints(OrderClosedEvent event) {
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
