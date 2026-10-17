package tw.niels.beverage_api_project.modules.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.model.MemberPoint;
import tw.niels.beverage_api_project.modules.member.domain.model.MemberProfile;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.model.ProductSnapshot;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderCheckoutService 單元測試（新增）。
 *
 * 驗證結帳 / 金額試算共用的計算規則：
 * - 促銷折扣上限為訂單總額，並以 PromotionContext 傳入品項
 * - 點數折抵上限為「扣除促銷後的金額」，超出上限的點數不扣
 * - 使用點數必須指定會員
 * - 同步扣點：點數不足拋出 DomainException（HTTP 422），足夠時寫回 MemberRepository
 * - 累點依品牌 earnRate 計算，訪客為 0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCheckoutService 結帳金額與點數規則")
class OrderCheckoutServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final Long MEMBER_ID = 200L;

    @Mock private PromotionService promotionService;
    @Mock private BrandPointConfigJpaRepository brandPointConfigJpaRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private UserService userService;

    private OrderCheckoutService service;

    /** 1 個品項：單價 50 × 2 = 100 元 */
    private List<OrderItem> items;
    private Money total;

    @BeforeEach
    void setUp() {
        service = new OrderCheckoutService(promotionService, brandPointConfigJpaRepository, memberRepository, userService);
        ProductSnapshot snapshot = new ProductSnapshot(999L, "珍珠奶茶", Money.of(new BigDecimal("50")), "飲品", "大杯");
        items = List.of(OrderItem.create(999L, 1001L, 2, Money.of(new BigDecimal("50")), null, List.of(), snapshot));
        total = Money.of(new BigDecimal("100"));

        // 品牌積點規則：每 1 點折 0.1 元、每 30 元累 1 點
        BrandPointConfigEntity config = new BrandPointConfigEntity();
        config.setRedeemRate(new BigDecimal("0.10"));
        config.setEarnRate(new BigDecimal("30"));
        lenient().when(brandPointConfigJpaRepository.findById(BRAND_ID)).thenReturn(Optional.of(config));
        lenient().when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("沒有促銷也沒有點數時，應付金額等於原價")
    void shouldReturnTotalWhenNoDiscount() {
        OrderCheckoutService.PricingResult result = service.calculate(BRAND_ID, items, total, null, 0L);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0");
        assertThat(result.pointsDiscount().amount()).isEqualByComparingTo("0");
        assertThat(result.pointsUsed()).isZero();
        assertThat(result.finalAmount().amount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("促銷折扣以 PromotionContext 傳入品項，且上限為訂單總額")
    void shouldCapPromotionDiscountAtTotal() {
        when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(new BigDecimal("150"));

        OrderCheckoutService.PricingResult result = service.calculate(BRAND_ID, items, total, null, 0L);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100");
        assertThat(result.finalAmount().amount()).isEqualByComparingTo("0");

        ArgumentCaptor<PromotionContext> captor = ArgumentCaptor.forClass(PromotionContext.class);
        verify(promotionService).calculateBestDiscount(captor.capture());
        PromotionContext ctx = captor.getValue();
        assertThat(ctx.brandId()).isEqualTo(BRAND_ID);
        assertThat(ctx.totalAmount()).isEqualByComparingTo("100");
        assertThat(ctx.lines()).hasSize(1);
        assertThat(ctx.lines().get(0).productId()).isEqualTo(999L);
        assertThat(ctx.lines().get(0).quantity()).isEqualTo(2);
        assertThat(ctx.lines().get(0).subtotal()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("點數折抵：在上限內時全部使用（100 點 × 0.1 = 10 元）")
    void shouldUseRequestedPointsWithinLimit() {
        OrderCheckoutService.PricingResult result = service.calculate(BRAND_ID, items, total, MEMBER_ID, 100L);

        assertThat(result.pointsUsed()).isEqualTo(100L);
        assertThat(result.pointsDiscount().amount()).isEqualByComparingTo("10");
        assertThat(result.finalAmount().amount()).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("點數折抵上限為扣除促銷後的金額，超出的點數不扣")
    void shouldCapPointsAtRemainingAmount() {
        when(promotionService.calculateBestDiscount(any(PromotionContext.class))).thenReturn(new BigDecimal("10"));

        // 剩餘 90 元，最多只能用 900 點
        OrderCheckoutService.PricingResult result = service.calculate(BRAND_ID, items, total, MEMBER_ID, 5000L);

        assertThat(result.pointsUsed()).isEqualTo(900L);
        assertThat(result.pointsDiscount().amount()).isEqualByComparingTo("90");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("100");
        assertThat(result.finalAmount().amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("使用點數卻沒有指定會員應回 400")
    void shouldRejectPointsWithoutMember() {
        assertThatThrownBy(() -> service.calculate(BRAND_ID, items, total, null, 10L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("同步扣點：點數不足拋出 DomainException（422），且不寫回會員")
    void shouldThrowDomainExceptionWhenPointsInsufficient() {
        when(memberRepository.findById(BRAND_ID, MEMBER_ID)).thenReturn(Optional.of(member(50L)));

        assertThatThrownBy(() -> service.deductPoints(BRAND_ID, MEMBER_ID, 100L, "測試扣點"))
                .isInstanceOf(DomainException.class);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("同步扣點：點數足夠時扣除並寫回會員")
    void shouldDeductPointsAndSaveMember() {
        Member member = member(500L);
        when(memberRepository.findById(BRAND_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        service.deductPoints(BRAND_ID, MEMBER_ID, 100L, "測試扣點");

        assertThat(member.getPoints().value()).isEqualTo(400L);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("扣點數為 0 時不查詢也不寫回會員")
    void shouldSkipWhenNoPointsToDeduct() {
        service.deductPoints(BRAND_ID, MEMBER_ID, 0L, "不扣點");

        verify(memberRepository, never()).findById(any(), any());
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("累點：依品牌 earnRate 無條件捨去；訪客為 0")
    void shouldCalculateEarnedPoints() {
        assertThat(service.calculateEarnedPoints(BRAND_ID, MEMBER_ID, Money.of(new BigDecimal("100")))).isEqualTo(3L);
        assertThat(service.calculateEarnedPoints(BRAND_ID, null, Money.of(new BigDecimal("100")))).isZero();
    }

    // ─── 新增（m-5）：停用會員不可綁定訂單或折抵點數 ─────────────────────────

    @Test
    @DisplayName("停用會員：同步扣點拋出 DomainException（422），點數不變且不寫回會員")
    void shouldRejectDeductPointsForInactiveMember() {
        Member inactive = member(500L, false);
        when(memberRepository.findById(BRAND_ID, MEMBER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.deductPoints(BRAND_ID, MEMBER_ID, 100L, "測試扣點"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("停用");
        assertThat(inactive.getPoints().value()).isEqualTo(500L);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("停用會員：金額試算的點數檢查拋出 DomainException（422）")
    void shouldRejectEnsurePointsForInactiveMember() {
        when(memberRepository.findById(BRAND_ID, MEMBER_ID)).thenReturn(Optional.of(member(500L, false)));

        assertThatThrownBy(() -> service.ensureSufficientPoints(BRAND_ID, MEMBER_ID, 100L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("停用會員：結帳綁定會員（建立會員快照）拋出 DomainException（422）")
    void shouldRejectMemberSnapshotForInactiveMember() {
        when(userService.getMember(BRAND_ID, MEMBER_ID)).thenReturn(memberUser(false));

        assertThatThrownBy(() -> service.resolveMemberSnapshot(BRAND_ID, MEMBER_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("停用");
    }

    @Test
    @DisplayName("啟用會員：正常建立會員快照")
    void shouldResolveMemberSnapshotForActiveMember() {
        when(userService.getMember(BRAND_ID, MEMBER_ID)).thenReturn(memberUser(true));

        MemberSnapshot snapshot = service.resolveMemberSnapshot(BRAND_ID, MEMBER_ID);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.userId()).isEqualTo(MEMBER_ID);
        assertThat(snapshot.fullName()).isEqualTo("王小明");
    }

    private static Member member(long points) {
        return member(points, true);
    }

    /** 新增：可指定啟用狀態的會員 Aggregate */
    private static Member member(long points, boolean active) {
        return new Member(MEMBER_ID, BRAND_ID,
                new MemberProfile("王小明", "0912345678", null, null, null),
                MemberPoint.of(points), null, active);
    }

    /** 新增：UserService.getMember 回傳的會員使用者（含 MemberProfileEntity） */
    private static UserEntity memberUser(boolean active) {
        UserEntity user = new UserEntity();
        user.setId(MEMBER_ID);
        user.setPrimaryPhone("0912345678");
        user.setIsActive(active);
        MemberProfileEntity profile = new MemberProfileEntity();
        profile.setFullName("王小明");
        user.setMemberProfile(profile);
        return user;
    }
}
