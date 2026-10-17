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
 * - 「只退真正扣過的點」：event.pointsUsed() 來自 Order.pointsUsed，而該值只在結帳交易內、
 *   member.consumePoints() 成功後才寫入（扣點是同步的，見 CLAUDE.md §5.3：餘額不足整筆回滾、不會有訂單）。
 *   尚未結帳（PENDING / HELD）的訂單 pointsUsed 恆為 0，因此取消時不會憑空產生點數
 * - 併發保護：MemberProfileEntity 的 @Version 樂觀鎖；衝突時重試（最多 3 次），
 *   仍失敗只記錄錯誤，不讓例外影響已提交的取消動作
 * ============================================================
 */
@Component
public class OrderCancelledMemberPointHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancelledMemberPointHandler.class);

    /** 樂觀鎖衝突時的最大嘗試次數 */
    private static final int MAX_ATTEMPTS = 3;

    private final MemberRepository memberRepository;
    // 每次嘗試都開一個全新的交易（REQUIRES_NEW），讓樂觀鎖衝突（commit 時才偵測到）可由外側捕捉並重試
    private final TransactionTemplate requiresNewTx;

    public OrderCancelledMemberPointHandler(MemberRepository memberRepository,
                                            PlatformTransactionManager transactionManager) {
        this.memberRepository = memberRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 處理訂單取消事件，退還會員已使用的積點。
     *
     * @param event 訂單取消事件，攜帶 memberId、brandId、pointsUsed
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCancelledEvent event) {
        // 訪客訂單或未使用積點，無需退還
        if (event.memberId() == null || event.pointsUsed() == null || event.pointsUsed() <= 0) {
            return;
        }

        // 修正：AFTER_COMMIT 監聽器的例外會冒泡到已成功提交的取消請求；
        // 改為樂觀鎖衝突重試、其他錯誤只記錄，絕不往外丟
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                requiresNewTx.executeWithoutResult(status -> refundPoints(event));
                return;
            } catch (OptimisticLockingFailureException e) {
                logger.warn("訂單 ID={} 會員 ID={} 退點遇到併發衝突，第 {}/{} 次嘗試",
                        event.orderId(), event.memberId(), attempt, MAX_ATTEMPTS);
            } catch (RuntimeException e) {
                logger.error("訂單 ID={} 會員 ID={} 積點退還失敗：{}",
                        event.orderId(), event.memberId(), e.getMessage(), e);
                return;
            }
        }
        logger.error("訂單 ID={} 會員 ID={} 積點退還重試 {} 次仍失敗，需人工補點（pointsUsed={}）",
                event.orderId(), event.memberId(), MAX_ATTEMPTS, event.pointsUsed());
    }

    /** 單次嘗試：載入會員、退點、儲存（在 REQUIRES_NEW 交易內執行） */
    private void refundPoints(OrderCancelledEvent event) {
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
