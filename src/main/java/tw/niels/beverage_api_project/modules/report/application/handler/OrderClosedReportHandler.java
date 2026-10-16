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

import java.math.BigDecimal;
import java.time.Clock;
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
 * 2. 以分店 ID + 今日日期（營業時區）執行原生 UPSERT（INSERT ... ON CONFLICT DO UPDATE）
 * 3. 由資料庫原子累加訂單數、營收、折扣、實收金額與付款方式細分，避免 read-modify-write 競爭
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
     * @param event 訂單完成事件，攜帶 orderId、brandId、storeId
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
        LocalDate today = LocalDate.now(clock);

        // 2. 計算本筆訂單對各付款方式的貢獻
        // 沒有付款方式的訂單不列入付款細分（金額仍計入總營收）；
        // 代碼為 CASH / CREDIT_CARD / THIRD_PARTY，其餘未知代碼同樣忽略
        BigDecimal finalAmount = nz(order.getFinalAmount());
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal creditCard = BigDecimal.ZERO;
        BigDecimal thirdParty = BigDecimal.ZERO;
        if (order.getPaymentMethod() != null && order.getPaymentMethod().getCode() != null) {
            switch (order.getPaymentMethod().getCode().toUpperCase()) {
                case "CASH" -> cash = finalAmount;
                case "CREDIT_CARD" -> creditCard = finalAmount;
                case "THIRD_PARTY" -> thirdParty = finalAmount;
                default -> logger.debug("訂單 ID={} 付款方式 {} 不在細分統計範圍內", event.orderId(),
                        order.getPaymentMethod().getCode());
            }
        }

        // 3. [B-6 修正] 以原生 UPSERT 原子累加今日統計
        // 原本「讀取 → 加總 → save」在兩筆訂單同時完成時會互相覆蓋而少算；
        // 現在由資料庫在唯一鍵 (store_id, date) 衝突時以 SQL 原子累加，不需要在應用層加鎖。
        dailyStoreStatsJpaRepository.upsertRealtimeStats(
                TsidUtil.nextId(), storeId, brandId, today,
                nz(order.getTotalAmount()), nz(order.getDiscountAmount()), finalAmount,
                cash, creditCard, thirdParty);

        logger.debug("訂單 ID={} 報表統計更新完成，分店 ID={} 日期 {}", event.orderId(), storeId, today);
    }

    /** null 安全：金額欄位為 null 時視為 0 */
    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
