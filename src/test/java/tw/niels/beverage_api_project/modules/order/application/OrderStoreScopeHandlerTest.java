package tw.niels.beverage_api_project.modules.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.application.command.AcceptOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.command.ProcessPaymentCommand;
import tw.niels.beverage_api_project.modules.order.application.command.StoreScope;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateHeldOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.command.UpdateOrderStatusCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.AcceptOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.handler.ProcessPaymentHandler;
import tw.niels.beverage_api_project.modules.order.application.handler.UpdateHeldOrderHandler;
import tw.niels.beverage_api_project.modules.order.application.handler.UpdateOrderStatusHandler;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderNumber;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.service.PaymentMethodService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 訂單寫入 Handler 的分店存取檢查單元測試（新增，M-1 分店資料隔離）。
 *
 * 驗證 PATCH /orders/{id}/status、PUT /orders/{id}、PATCH /orders/{id}/checkout、PATCH /orders/{id}/accept
 * 對應的 Handler 在交易內載入訂單後，會以 Command 帶入的 StoreScope 比對訂單所屬分店：
 * - 綁定分店 1 的員工操作分店 2 的訂單 → AccessDeniedException（HTTP 403），訂單不變、不儲存、不扣點
 * - 同分店員工、未綁店的品牌管理員（StoreScope.brandWide()）→ 正常執行
 * - Command 不可省略 StoreScope（null → NullPointerException），避免新呼叫端意外取得全品牌權限
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("訂單寫入 Handler 分店存取檢查（M-1）")
class OrderStoreScopeHandlerTest {

    private static final Long BRAND_ID = 1L;
    private static final Long ORDER_ID = 500L;
    /** 訂單所屬分店 */
    private static final Long ORDER_STORE_ID = 2L;
    /** 呼叫者綁定的其他分店 */
    private static final Long OTHER_STORE_ID = 1L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderCheckoutService orderCheckoutService;
    @Mock private OrderItemFactory orderItemFactory;
    @Mock private PaymentMethodService paymentMethodService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderItem sampleItem;

    @BeforeEach
    void setUp() {
        ProductSnapshot snapshot = new ProductSnapshot(999L, "珍珠奶茶", Money.of(new BigDecimal("50")), "飲品", "大杯");
        sampleItem = OrderItem.create(999L, 1001L, 1, Money.of(new BigDecimal("50")), null, List.of(), snapshot);
    }

    /** 分店 2 的 POS 訂單（PENDING） */
    private Order pendingOrderOfStore2() {
        return Order.place(ORDER_ID, BRAND_ID, ORDER_STORE_ID, 10L,
                OrderNumber.of("2-20261017-0001"), List.of(sampleItem), null);
    }

    /** 分店 2 的線上訂單（AWAITING_ACCEPTANCE） */
    private Order onlineOrderOfStore2() {
        return Order.placeOnline(ORDER_ID, BRAND_ID, ORDER_STORE_ID, null, null,
                "GUEST:abc", "ticket-1", OrderNumber.of("2-20261017-0002"), List.of(sampleItem), null);
    }

    @Nested
    @DisplayName("UpdateOrderStatusHandler（PATCH /orders/{id}/status）")
    class UpdateStatus {

        private UpdateOrderStatusHandler handler;

        @BeforeEach
        void setUp() {
            handler = new UpdateOrderStatusHandler(orderRepository, orderCheckoutService, eventPublisher);
        }

        @Test
        @DisplayName("綁定其他分店的員工取消訂單 → 403，訂單狀態不變且不儲存")
        void shouldRejectCancelFromOtherStore() {
            Order order = pendingOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> handler.handle(new UpdateOrderStatusCommand(
                    BRAND_ID, ORDER_ID,
                    tw.niels.beverage_api_project.modules.order.enums.OrderStatus.CANCELLED,
                    StoreScope.ofStore(OTHER_STORE_ID))))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("同分店員工可以取消訂單")
        void shouldAllowSameStore() {
            Order order = pendingOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            handler.handle(new UpdateOrderStatusCommand(
                    BRAND_ID, ORDER_ID,
                    tw.niels.beverage_api_project.modules.order.enums.OrderStatus.CANCELLED,
                    StoreScope.ofStore(ORDER_STORE_ID)));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("未綁店的品牌管理員（全品牌範圍）可以操作任一分店的訂單")
        void shouldAllowBrandWideAdmin() {
            Order order = pendingOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            handler.handle(new UpdateOrderStatusCommand(
                    BRAND_ID, ORDER_ID,
                    tw.niels.beverage_api_project.modules.order.enums.OrderStatus.HELD,
                    StoreScope.brandWide()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Command 不可省略 StoreScope")
        void shouldRequireStoreScope() {
            assertThatThrownBy(() -> new UpdateOrderStatusCommand(
                    BRAND_ID, ORDER_ID,
                    tw.niels.beverage_api_project.modules.order.enums.OrderStatus.CANCELLED,
                    null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ProcessPaymentHandler（PATCH /orders/{id}/checkout）")
    class ProcessPayment {

        @Test
        @DisplayName("綁定其他分店的員工結帳 → 403，且不查付款方式、不扣點、不儲存")
        void shouldRejectCheckoutFromOtherStore() {
            ProcessPaymentHandler handler = new ProcessPaymentHandler(
                    orderRepository, orderCheckoutService, paymentMethodService, eventPublisher);
            Order order = pendingOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> handler.handle(new ProcessPaymentCommand(
                    BRAND_ID, ORDER_ID, 200L, 100L, "CASH", StoreScope.ofStore(OTHER_STORE_ID))))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verifyNoInteractions(paymentMethodService);
            verify(orderCheckoutService, never()).deductPoints(any(), any(), anyLong(), anyString());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("UpdateHeldOrderHandler（PUT /orders/{id}）")
    class UpdateHeldOrder {

        @Test
        @DisplayName("綁定其他分店的員工修改暫存單 → 403，且不建立新品項、不儲存")
        void shouldRejectUpdateFromOtherStore() {
            UpdateHeldOrderHandler handler = new UpdateHeldOrderHandler(orderRepository, orderItemFactory, eventPublisher);
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(pendingOrderOfStore2()));
            List<OrderItemDto> items = List.of(new OrderItemDto(999L, 1001L, 2, "少冰", List.of()));

            assertThatThrownBy(() -> handler.handle(new UpdateHeldOrderCommand(
                    BRAND_ID, ORDER_ID,
                    tw.niels.beverage_api_project.modules.order.enums.OrderStatus.HELD,
                    items, StoreScope.ofStore(OTHER_STORE_ID))))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(orderItemFactory);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("AcceptOrderHandler（PATCH /orders/{id}/accept）")
    class AcceptOrder {

        private AcceptOrderHandler handler;

        @BeforeEach
        void setUp() {
            handler = new AcceptOrderHandler(orderRepository, eventPublisher);
        }

        @Test
        @DisplayName("綁定其他分店的員工接單 → 403，訂單維持待接單")
        void shouldRejectAcceptFromOtherStore() {
            Order order = onlineOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> handler.handle(new AcceptOrderCommand(
                    BRAND_ID, ORDER_ID, StoreScope.ofStore(OTHER_STORE_ID))))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_ACCEPTANCE);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("同分店員工接單 → PREPARING")
        void shouldAllowAcceptFromSameStore() {
            Order order = onlineOrderOfStore2();
            when(orderRepository.findByBrandIdAndId(BRAND_ID, ORDER_ID)).thenReturn(Optional.of(order));

            handler.handle(new AcceptOrderCommand(BRAND_ID, ORDER_ID, StoreScope.ofStore(ORDER_STORE_ID)));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
            verify(orderRepository).save(order);
        }
    }
}
