package tw.niels.beverage_api_project.modules.order.application.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.Order;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderStatus;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.service.OrderNumberService;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.store.service.StoreService;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlaceOnlineOrderHandler 單元測試（新增，item 5：線上訂單套用促銷折扣）。
 *
 * 使用真正的 OrderCheckoutService（與 POS 結帳、/orders/preview 共用的金額計算元件），
 * 只 mock 促銷模組與資料存取，驗證線上訂單建立時：
 * - totalAmount / discountAmount / finalAmount 與 OrderCheckoutService.calculate()（preview 使用的同一個方法）結果一致
 * - 不使用點數折抵（pointsUsed = 0）
 * - 促銷折扣超過總額時以總額為上限，應付金額為 0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOnlineOrderHandler 線上訂單促銷折扣")
class PlaceOnlineOrderHandlerTest {

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 10L;
    private static final Long ORDER_ID = 900_000_000_000_000_001L;
    private static final String TICKET_ID = "TICKET-001";

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemFactory orderItemFactory;
    @Mock private OrderNumberService orderNumberService;
    @Mock private StoreService storeService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // OrderCheckoutService 的相依（以 @InjectMocks 建立真正的 OrderCheckoutService，建構子變動時仍能自動注入）
    @Mock private PromotionService promotionService;
    @Mock private BrandPointConfigJpaRepository brandPointConfigJpaRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private UserService userService;
    @InjectMocks private OrderCheckoutService orderCheckoutService;

    private PlaceOnlineOrderHandler handler;
    /** 珍珠奶茶 50 元 × 2 = 100 元 */
    private List<OrderItem> items;
    private PlaceOnlineOrderCommand command;

    @BeforeEach
    void setUp() {
        handler = new PlaceOnlineOrderHandler(orderRepository, orderItemFactory, orderNumberService,
                orderCheckoutService, storeService, eventPublisher);

        ProductSnapshot snapshot = new ProductSnapshot(999L, "珍珠奶茶", Money.of(new BigDecimal("50")), "飲品", "大杯");
        items = List.of(OrderItem.create(999L, 1001L, 2, Money.of(new BigDecimal("50")), null, List.of(), snapshot));
        List<OrderItemDto> requestItems = List.of();
        command = new PlaceOnlineOrderCommand(TICKET_ID, BRAND_ID, STORE_ID, null, "GUEST:abc", requestItems, "少冰");

        when(orderItemFactory.createItems(eq(BRAND_ID), anyList())).thenReturn(items);
        when(orderNumberService.nextOrderNumber(STORE_ID)).thenReturn("10-20261017-0001");
        when(orderRepository.nextId()).thenReturn(ORDER_ID);
    }

    private Order placeAndCaptureSavedOrder() {
        PlaceOnlineOrderHandler.Result result = handler.handle(command);
        assertThat(result.created()).isTrue();
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("線上訂單套用促銷折扣：金額與 preview 使用的 OrderCheckoutService.calculate() 一致，且不折抵點數")
    void onlineOrder_shouldGetPromotionDiscountMatchingPreview() {
        when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(new BigDecimal("15"));

        Order order = placeAndCaptureSavedOrder();

        // 促銷模組收到的 PromotionContext 含品牌、品項與總額（在呼叫 preview 計算前驗證，確保只被建單呼叫一次）
        ArgumentCaptor<PromotionContext> context = ArgumentCaptor.forClass(PromotionContext.class);
        verify(promotionService).calculateBestDiscount(context.capture());
        assertThat(context.getValue().brandId()).isEqualTo(BRAND_ID);
        assertThat(context.getValue().totalAmount()).isEqualByComparingTo("100");
        assertThat(context.getValue().lines()).hasSize(1);

        // preview（OrderPreviewHandler）以相同參數（品項、總額、不使用點數）呼叫 calculate()
        OrderCheckoutService.PricingResult preview = orderCheckoutService.calculate(
                BRAND_ID, items, Money.of(new BigDecimal("100")), null, 0L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_ACCEPTANCE);
        assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("100");
        assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("15");
        assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("85");
        assertThat(order.getTotalAmount().isEqualTo(preview.totalAmount())).isTrue();
        assertThat(order.getDiscountAmount().isEqualTo(preview.totalDiscount())).isTrue();
        assertThat(order.getFinalAmount().isEqualTo(preview.finalAmount())).isTrue();
        assertThat(order.getPointsUsed()).isZero();
    }

    @Test
    @DisplayName("沒有適用的促銷：折扣 0，應付金額 = 總額")
    void onlineOrder_withoutPromotion_shouldChargeFullAmount() {
        when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(BigDecimal.ZERO);

        Order order = placeAndCaptureSavedOrder();

        assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("0");
        assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("促銷折扣超過總額：以總額為上限，應付金額為 0")
    void onlineOrder_discountGreaterThanTotal_shouldBeCapped() {
        when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(new BigDecimal("150"));

        Order order = placeAndCaptureSavedOrder();

        assertThat(order.getDiscountAmount().amount()).isEqualByComparingTo("100");
        assertThat(order.getFinalAmount().amount()).isEqualByComparingTo("0");
    }
}
