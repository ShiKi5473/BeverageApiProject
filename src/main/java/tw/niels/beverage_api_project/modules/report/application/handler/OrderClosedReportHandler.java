package tw.niels.beverage_api_project.modules.report.application.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tw.niels.beverage_api_project.common.util.TsidUtil;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;
import tw.niels.beverage_api_project.modules.report.repository.DailyStoreStatsJpaRepository;
import tw.niels.beverage_api_project.modules.report.service.PaymentBreakdown;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * ============================================================
 * Bounded Context: Report
 * Layer: Application - Event Handler
 * ============================================================
 *
 * 訂單完成後即時更新每日銷售統計事件監聽器
 *
 * 訂閱 {@link OrderClosedEvent}，在訂單事務提交後（AFTER_COMMIT）
 * 開啟獨立新事務，即時累加當日分店的銷售統計資料。
 *
 * 處理流程：
 * 1. 載入訂單取得實際金額（totalAmount、discountAmount、finalAmount）與付款方式
 * 2. 以分店 ID + 訂單完成日期（營業時區；沒有完成時間時退回今天）執行原生 UPSERT（INSERT ... ON CONFLICT DO UPDATE）
 * 3. 由資料庫原子累加訂單數、營收、折扣、實收金額與付款方式細分（現金 / 信用卡 / 第三方 / 未指定），避免 read-modify-write 競爭
 *
 * 設計決策：
 * - 使用 AFTER_COMMIT + REQUIRES_NEW，確保統計資料僅在訂單確實提交後才更新
 * - 與每日批次結算（StoreSettlementService）並行：批次結算以 DELETE + INSERT
 *   方式重建完整日結，覆蓋此處的即時累計值；此 Handler 僅提供日中即時查閱
 * - 若載入訂單失敗，記錄錯誤日誌但不拋出例外
 * ============================================================
 */
@Component
public class OrderClosedReportHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderClosedReportHandler.class);

    private final OrderJpaRepository orderJpaRepository;
    private final DailyStoreStatsJpaRepository dailyStoreStatsJpaRepository;
    // [B-7] 以營業時區 (TimeConfig) 的 Clock 決定「今天」，不再用 JVM 預設時區
    private final Clock clock;

    public OrderClosedReportHandler(OrderJpaRepository orderJpaRepository,
                                    DailyStoreStatsJpaRepository dailyStoreStatsJpaRepository,
                                    Clock clock) {
        this.orderJpaRepository = orderJpaRepository;
        this.dailyStoreStatsJpaRepository = dailyStoreStatsJpaRepository;
        this.clock = clock;
    }

    /**
     * 處理訂單完成事件，即時更新當日銷售統計。
     *
     * <p>fallbackExecution = true：事件若是在沒有交易的情境下發布，預設的 AFTER_COMMIT 監聽器會被靜默略過而漏算；
     * 開啟後改為立即執行 (本方法自己會以 REQUIRES_NEW 開新交易)。</p>
     *
     * @param event 訂單完成事件，攜帶 orderId、brandId、storeId
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderClosedEvent event) {
        // 1. 載入訂單取得詳細金額資訊
        OrderEntity order = orderJpaRepository.findByIdForSystem(event.orderId()).orElse(null);
        if (order == null) {
            logger.error("報表更新失敗：找不到訂單 ID={}", event.orderId());
            return;
        }

        Long storeId = order.getStore().getId();
        Long brandId = order.getBrand().getId();
        // [B-7 修正] 統計日期以「訂單完成時間」（營業時區）為準，與批次日結 (completedTime 篩選) 一致；
        // 若事件在跨午夜時才被處理，用 LocalDate.now(clock) 會把前一天的訂單算到隔天。沒有完成時間才退回今天。
        LocalDate statDate = order.getCompletedTime() != null
                ? Instant.ofEpochMilli(order.getCompletedTime().getTime()).atZone(clock.getZone()).toLocalDate()
                : LocalDate.now(clock);

        // 2. 計算本筆訂單對各付款方式的貢獻
        // 代碼 CASH / CREDIT_CARD / THIRD_PARTY 各自歸欄，沒有付款方式或未知代碼歸入「未指定」(與批次日結共用同一套規則)
        BigDecimal finalAmount = nz(order.getFinalAmount());
        String paymentCode = order.getPaymentMethod() != null ? order.getPaymentMethod().getCode() : null;
        PaymentBreakdown payment = PaymentBreakdown.of(paymentCode, finalAmount);

        // 3. [B-6 修正] 以原生 UPSERT 原子累加當日統計
        // 原本「讀取 → 加總 → save」在兩筆訂單同時完成時會互相覆蓋而少算；
        // 現在由資料庫在唯一鍵 (store_id, date) 衝突時以 SQL 原子累加，不需要在應用層加鎖。
        dailyStoreStatsJpaRepository.upsertRealtimeStats(
                TsidUtil.nextId(), storeId, brandId, statDate,
                nz(order.getTotalAmount()), nz(order.getDiscountAmount()), finalAmount,
                payment.cash(), payment.creditCard(), payment.thirdParty(), payment.unspecified());

        logger.debug("訂單 ID={} 報表統計更新完成，分店 ID={} 日期 {}", event.orderId(), storeId, statDate);
    }

    /** null 安全：金額欄位為 null 時視為 0 */
    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
