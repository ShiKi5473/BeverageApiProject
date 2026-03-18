package tw.niels.beverage_api_project.modules.order.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderCancelledEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderPlacedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderStatusChangedEvent;
import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.Money;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order Aggregate Root 單元測試
 *
 * 測試範圍：
 * - 靜態工廠方法 place()
 * - 所有狀態轉換業務方法
 * - 非法狀態轉換應拋出 OrderDomainException
 * - 領域事件的收集與清空（pullDomainEvents）
 * - 品項更新邏輯
 * - 取消訂單的積點資訊攜帶
 *
 * 注意：此為純粹的 POJO 單元測試，不需要 Spring Context，執行速度極快。
 */
@DisplayName("Order Aggregate Root 測試")
class OrderTest {

    // ─── 測試用固定資料 ──────────────────────────────────────────────────────

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 10L;
    private static final Long STAFF_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final Long PAYMENT_METHOD_ID = 1L;

    /** 用於建立有效訂單品項 */
    private OrderItem sampleItem;

    @BeforeEach
    void setUp() {
        // 建立測試用商品快照
        ProductSnapshot snapshot = new ProductSnapshot(
                999L,
                "珍珠奶茶",
                Money.of(new BigDecimal("50.00")),
                "飲品",
                "大杯"
        );
        // 建立測試用訂單品項（1 份，50 元）
        sampleItem = OrderItem.create(999L, 1001L, 1,
                Money.of(new BigDecimal("50.00")), null, List.of(), snapshot);
    }

    /**
     * 建立一個初始狀態為 PENDING 的標準測試訂單。
     * 注意：place() 的訂單 id 為 null（由 Infrastructure 設定），
     * 因此事件中的 orderId 也會是 null。
     */
    private Order createPendingOrder() {
        return Order.place(
                BRAND_ID, STORE_ID, STAFF_ID,
                OrderNumber.of("A001"),
                List.of(sampleItem),
                "測試備註"
        );
    }

    // ─── 測試：place() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("place() - 建立新訂單")
    class Place {

        @Test
        @DisplayName("成功建立訂單，初始狀態為 PENDING")
        void shouldCreateOrderWithPendingStatus() {
            // When
            Order order = createPendingOrder();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getBrandId()).isEqualTo(BRAND_ID);
            assertThat(order.getStoreId()).isEqualTo(STORE_ID);
            assertThat(order.getOrderNumber().value()).isEqualTo("A001");
            assertThat(order.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("建立訂單後自動計算總金額")
        void shouldCalculateTotalAmountFromItems() {
            // When
            Order order = createPendingOrder();

            // Then - totalAmount = sampleItem.subtotal = 50.00
            assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("50.00");
            // 初始無折扣，finalAmount = totalAmount
            assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("50.00");
            assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("建立訂單後應發布 OrderPlacedEvent")
        void shouldPublishOrderPlacedEvent() {
            // When
            Order order = createPendingOrder();
            List<Object> events = order.pullDomainEvents();

            // Then
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(OrderPlacedEvent.class);

            OrderPlacedEvent event = (OrderPlacedEvent) events.get(0);
            assertThat(event.brandId()).isEqualTo(BRAND_ID);
            assertThat(event.storeId()).isEqualTo(STORE_ID);
            assertThat(event.orderNumber()).isEqualTo("A001");
        }

        @Test
        @DisplayName("空品項應拋出 OrderDomainException")
        void shouldThrowExceptionWhenItemsAreEmpty() {
            assertThatThrownBy(() ->
                    Order.place(BRAND_ID, STORE_ID, STAFF_ID,
                            OrderNumber.of("A002"), List.of(), null)
            )
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("至少一個品項");
        }

        @Test
        @DisplayName("null 品項應拋出 OrderDomainException")
        void shouldThrowExceptionWhenItemsAreNull() {
            assertThatThrownBy(() ->
                    Order.place(BRAND_ID, STORE_ID, STAFF_ID,
                            OrderNumber.of("A003"), null, null)
            )
                    .isInstanceOf(OrderDomainException.class);
        }
    }

    // ─── 測試：hold() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("hold() - 暫存訂單")
    class Hold {

        @Test
        @DisplayName("PENDING 狀態可成功暫存，狀態變為 HELD")
        void shouldTransitionFromPendingToHeld() {
            // Given
            Order order = createPendingOrder();
            order.pullDomainEvents(); // 清除 place 產生的事件

            // When
            order.hold();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);
        }

        @Test
        @DisplayName("hold() 應發布 OrderStatusChangedEvent（PENDING → HELD）")
        void shouldPublishStatusChangedEvent() {
            // Given
            Order order = createPendingOrder();
            order.pullDomainEvents(); // 清除 place 產生的事件

            // When
            order.hold();
            List<Object> events = order.pullDomainEvents();

            // Then
            assertThat(events).hasSize(1);
            OrderStatusChangedEvent event = (OrderStatusChangedEvent) events.get(0);
            assertThat(event.oldStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(event.newStatus()).isEqualTo(OrderStatus.HELD);
        }

        @Test
        @DisplayName("非 PENDING 狀態呼叫 hold() 應拋出 OrderDomainException")
        void shouldThrowExceptionWhenNotPending() {
            // Given - 訂單已暫存為 HELD
            Order order = createPendingOrder();
            order.hold();
            order.pullDomainEvents();

            // When / Then - 再次 hold 應拋出例外
            assertThatThrownBy(order::hold)
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("HELD");
        }
    }

    // ─── 測試：processPayment() ──────────────────────────────────────────────

    @Nested
    @DisplayName("processPayment() - 結帳付款")
    class ProcessPayment {

        @Test
        @DisplayName("PENDING 狀態可成功結帳，狀態變為 PREPARING")
        void shouldTransitionFromPendingToPreparing() {
            // Given
            Order order = createPendingOrder();
            order.pullDomainEvents();

            // When - 無折扣、無積點的訪客結帳
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
            assertThat(order.getPaymentMethodId()).isEqualTo(PAYMENT_METHOD_ID);
        }

        @Test
        @DisplayName("HELD 狀態可成功結帳，狀態變為 PREPARING")
        void shouldTransitionFromHeldToPreparing() {
            // Given
            Order order = createPendingOrder();
            order.hold();
            order.pullDomainEvents();

            // When
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("結帳時計算折扣後的最終金額")
        void shouldCalculateFinalAmountAfterDiscount() {
            // Given - 原價 50 元，折扣 10 元
            Order order = createPendingOrder();
            order.pullDomainEvents();
            Money discount = Money.of(new BigDecimal("10.00"));

            // When
            order.processPayment(PAYMENT_METHOD_ID, discount, 0L, null, null);

            // Then
            assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("10.00");
            assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("折扣超過原價時最終金額歸零")
        void shouldSetFinalAmountToZeroWhenDiscountExceedsTotal() {
            // Given - 原價 50 元，折扣 100 元（超過原價）
            Order order = createPendingOrder();
            order.pullDomainEvents();
            Money hugeDiscount = Money.of(new BigDecimal("100.00"));

            // When
            order.processPayment(PAYMENT_METHOD_ID, hugeDiscount, 0L, null, null);

            // Then - 最終金額最低為 0
            assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("結帳時設定會員資訊與快照")
        void shouldSetMemberInfoDuringPayment() {
            // Given
            Order order = createPendingOrder();
            order.pullDomainEvents();
            MemberSnapshot snapshot = new MemberSnapshot(MEMBER_ID, "王小明", "0912345678", "test@test.com");

            // When
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 100L, MEMBER_ID, snapshot);

            // Then
            assertThat(order.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(order.getPointsUsed()).isEqualTo(100L);
            assertThat(order.getMemberSnapshot().fullName()).isEqualTo("王小明");
        }

        @Test
        @DisplayName("PREPARING 狀態呼叫 processPayment() 應拋出例外")
        void shouldThrowExceptionWhenNotInPayableStatus() {
            // Given - 訂單已進入 PREPARING
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);
            order.pullDomainEvents();

            // When / Then
            assertThatThrownBy(() ->
                    order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null)
            )
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("PREPARING");
        }
    }

    // ─── 測試：accept() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("accept() - 店家接單")
    class Accept {

        private Order awaitingOrder() {
            // 模擬線上訂單（AWAITING_ACCEPTANCE 狀態）
            return Order.reconstitute(
                    1L, BRAND_ID, STORE_ID, null, MEMBER_ID,
                    OrderNumber.of("B001"), OrderStatus.AWAITING_ACCEPTANCE,
                    Money.of(new BigDecimal("80.00")), Money.ZERO,
                    Money.of(new BigDecimal("80.00")),
                    0L, 0L, PAYMENT_METHOD_ID,
                    null, null, null,
                    List.of(sampleItem),
                    new MemberSnapshot(MEMBER_ID, "陳大明", "0988888888", "chen@test.com")
            );
        }

        @Test
        @DisplayName("AWAITING_ACCEPTANCE 狀態可接單，狀態變為 PREPARING")
        void shouldTransitionToPreparingAfterAccept() {
            // Given
            Order order = awaitingOrder();

            // When
            order.accept();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("accept() 應發布 OrderStatusChangedEvent")
        void shouldPublishStatusChangedEvent() {
            // Given
            Order order = awaitingOrder();

            // When
            order.accept();
            List<Object> events = order.pullDomainEvents();

            // Then
            assertThat(events).hasSize(1);
            OrderStatusChangedEvent event = (OrderStatusChangedEvent) events.get(0);
            assertThat(event.oldStatus()).isEqualTo(OrderStatus.AWAITING_ACCEPTANCE);
            assertThat(event.newStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("非 AWAITING_ACCEPTANCE 狀態呼叫 accept() 應拋出例外")
        void shouldThrowExceptionWhenNotAwaiting() {
            // Given - 現場訂單（PENDING 狀態）
            Order order = createPendingOrder();

            // When / Then
            assertThatThrownBy(order::accept)
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("AWAITING_ACCEPTANCE");
        }
    }

    // ─── 測試：markReadyForPickup() ─────────────────────────────────────────

    @Nested
    @DisplayName("markReadyForPickup() - 標記製作完成")
    class MarkReadyForPickup {

        @Test
        @DisplayName("PREPARING 狀態可標記完成，狀態變為 READY_FOR_PICKUP")
        void shouldTransitionToReadyForPickup() {
            // Given
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);
            order.pullDomainEvents();

            // When
            order.markReadyForPickup();

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        }

        @Test
        @DisplayName("非 PREPARING 狀態應拋出例外")
        void shouldThrowExceptionWhenNotPreparing() {
            Order order = createPendingOrder();
            assertThatThrownBy(order::markReadyForPickup)
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("PREPARING");
        }
    }

    // ─── 測試：close() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("close() - 完成訂單")
    class Close {

        /** 建立 READY_FOR_PICKUP 狀態的訂單 */
        private Order readyOrder() {
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, MEMBER_ID,
                    new MemberSnapshot(MEMBER_ID, "李小花", "0922222222", "li@test.com"));
            order.markReadyForPickup();
            order.pullDomainEvents();
            return order;
        }

        @Test
        @DisplayName("READY_FOR_PICKUP 狀態可完成訂單，狀態變為 CLOSED")
        void shouldTransitionToClosedStatus() {
            // Given
            Order order = readyOrder();

            // When
            order.close(25L);

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
            assertThat(order.getPointsEarned()).isEqualTo(25L);
            assertThat(order.getCompletedTime()).isNotNull();
        }

        @Test
        @DisplayName("close() 應發布 OrderClosedEvent，攜帶積點與金額資訊")
        void shouldPublishOrderClosedEventWithCorrectData() {
            // Given
            Order order = readyOrder();

            // When
            order.close(25L);
            List<Object> events = order.pullDomainEvents();

            // Then
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(OrderClosedEvent.class);

            OrderClosedEvent event = (OrderClosedEvent) events.get(0);
            assertThat(event.pointsEarned()).isEqualTo(25L);
            assertThat(event.memberId()).isEqualTo(MEMBER_ID);
            assertThat(event.finalAmount().amount()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("非 READY_FOR_PICKUP 狀態呼叫 close() 應拋出例外")
        void shouldThrowExceptionWhenNotReadyForPickup() {
            Order order = createPendingOrder();
            assertThatThrownBy(() -> order.close(0L))
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("READY_FOR_PICKUP");
        }

        @Test
        @DisplayName("close() 訪客訂單 pointsEarned 為 0，memberId 為 null")
        void shouldHandleGuestOrderClose() {
            // Given - 訪客訂單（無會員）
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);
            order.markReadyForPickup();
            order.pullDomainEvents();

            // When
            order.close(0L);
            List<Object> events = order.pullDomainEvents();

            // Then
            OrderClosedEvent event = (OrderClosedEvent) events.get(0);
            assertThat(event.memberId()).isNull();
            assertThat(event.pointsEarned()).isEqualTo(0L);
        }
    }

    // ─── 測試：cancel() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel() - 取消訂單")
    class Cancel {

        @Test
        @DisplayName("PENDING 狀態可取消訂單")
        void shouldCancelPendingOrder() {
            Order order = createPendingOrder();
            order.pullDomainEvents();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("HELD 狀態可取消訂單")
        void shouldCancelHeldOrder() {
            Order order = createPendingOrder();
            order.hold();
            order.pullDomainEvents();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("PREPARING 狀態可取消訂單")
        void shouldCancelPreparingOrder() {
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);
            order.pullDomainEvents();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("取消訂單應發布 OrderCancelledEvent，攜帶已使用積點資訊")
        void shouldPublishCancelledEventWithPointsInfo() {
            // Given - 使用 100 點結帳的訂單進入 PREPARING
            Order order = createPendingOrder();
            MemberSnapshot snapshot = new MemberSnapshot(MEMBER_ID, "趙大帥", "0911111111", null);
            order.processPayment(PAYMENT_METHOD_ID, Money.of(new BigDecimal("5.00")), 100L, MEMBER_ID, snapshot);
            order.pullDomainEvents();

            // When
            order.cancel();
            List<Object> events = order.pullDomainEvents();

            // Then
            assertThat(events).hasSize(1);
            OrderCancelledEvent event = (OrderCancelledEvent) events.get(0);
            assertThat(event.cancelledFromStatus()).isEqualTo(OrderStatus.PREPARING);
            assertThat(event.pointsUsed()).isEqualTo(100L); // 需退還
            assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("CLOSED 訂單不可取消，應拋出 OrderDomainException")
        void shouldThrowExceptionWhenOrderIsClosed() {
            // Given - 完成取餐的訂單
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);
            order.markReadyForPickup();
            order.close(0L);

            // When / Then
            assertThatThrownBy(order::cancel)
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("最終態");
        }

        @Test
        @DisplayName("已取消的訂單不可再次取消")
        void shouldThrowExceptionWhenAlreadyCancelled() {
            // Given
            Order order = createPendingOrder();
            order.cancel();

            // When / Then
            assertThatThrownBy(order::cancel)
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("最終態");
        }
    }

    // ─── 測試：updateItems() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("updateItems() - 更新品項")
    class UpdateItems {

        @Test
        @DisplayName("PENDING 狀態可更新品項")
        void shouldUpdateItemsWhenPending() {
            // Given
            Order order = createPendingOrder();
            ProductSnapshot newSnapshot = new ProductSnapshot(888L, "綠茶", Money.of(new BigDecimal("40.00")), "飲品", "中杯");
            OrderItem newItem = OrderItem.create(888L, 2001L, 2, Money.of(new BigDecimal("40.00")), null, List.of(), newSnapshot);

            // When
            order.updateItems(List.of(newItem), Money.of(new BigDecimal("80.00")));

            // Then
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("80.00");
            // 更新後折扣重置
            assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("HELD 狀態可更新品項")
        void shouldUpdateItemsWhenHeld() {
            // Given
            Order order = createPendingOrder();
            order.hold();
            order.pullDomainEvents();

            // When
            order.updateItems(List.of(sampleItem), Money.of(new BigDecimal("50.00")));

            // Then - 狀態不變，仍為 HELD
            assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);
        }

        @Test
        @DisplayName("PREPARING 狀態更新品項應拋出例外")
        void shouldThrowExceptionWhenPreparing() {
            // Given
            Order order = createPendingOrder();
            order.processPayment(PAYMENT_METHOD_ID, Money.ZERO, 0L, null, null);

            // When / Then
            assertThatThrownBy(() -> order.updateItems(List.of(sampleItem), Money.of(new BigDecimal("50.00"))))
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("PREPARING");
        }
    }

    // ─── 測試：pullDomainEvents() ────────────────────────────────────────────

    @Nested
    @DisplayName("pullDomainEvents() - 領域事件管理")
    class PullDomainEvents {

        @Test
        @DisplayName("pullDomainEvents() 回傳事件後清空列表")
        void shouldClearEventsAfterPull() {
            // Given
            Order order = createPendingOrder();
            assertThat(order.pullDomainEvents()).hasSize(1); // OrderPlacedEvent

            // When - 再次呼叫
            List<Object> secondPull = order.pullDomainEvents();

            // Then - 已清空
            assertThat(secondPull).isEmpty();
        }

        @Test
        @DisplayName("多個操作累積多個事件，一次 pull 全取出")
        void shouldAccumulateMultipleEvents() {
            // Given - place → hold → cancel
            Order order = createPendingOrder();
            order.hold();
            order.cancel();

            // When
            List<Object> events = order.pullDomainEvents();

            // Then - 3 個事件（OrderPlacedEvent + 2 個 OrderStatusChangedEvent/OrderCancelledEvent）
            assertThat(events).hasSize(3);
            assertThat(events.get(0)).isInstanceOf(OrderPlacedEvent.class);
            assertThat(events.get(1)).isInstanceOf(OrderStatusChangedEvent.class); // PENDING → HELD
            assertThat(events.get(2)).isInstanceOf(OrderCancelledEvent.class);
        }
    }
}
