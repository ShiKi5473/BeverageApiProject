package tw.niels.beverage_api_project.modules.order.application.service;

import org.springframework.stereotype.Component;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;
import tw.niels.beverage_api_project.modules.order.domain.model.MemberSnapshot;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.domain.service.OrderPricingDomainService;
import tw.niels.beverage_api_project.modules.promotion.dto.PromotionContext;
import tw.niels.beverage_api_project.modules.promotion.service.PromotionService;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Application - Service（結帳共用元件）
 * ============================================================
 *
 * 結帳共用服務（新增）。
 *
 * 新增原因（B-1 點數、B-2 累點、B-3 促銷、B-8 金額試算）：
 * 原本 PosCheckoutHandler、ProcessPaymentHandler 各自計算折扣，且
 * - 促銷折扣是 TODO（固定 0）；
 * - 點數在 AFTER_COMMIT 才扣、事前不檢查餘額 → 點數不足時折扣已生效、可憑空產生點數；
 * - 點數折抵沒有上限；累點固定 close(0L)。
 * 現在統一由此元件處理，並供 POS 結帳、既有訂單結帳、金額試算（preview）共用，確保三者金額一致：
 * 1. 促銷折扣：PromotionService.calculateBestDiscount(PromotionContext)，上限為訂單總額
 * 2. 點數折抵：上限為「扣除促銷後的金額」，超出上限的點數不扣（無條件捨去成整數點）
 * 3. 同步扣點：在呼叫端的結帳交易內 Member.consumePoints() + MemberRepository.save()，
 *    餘額不足轉成 DomainException（HTTP 422），整筆交易回滾、不建立訂單
 * 4. 累點：依品牌 earnRate 與 OrderPricingDomainService.calculateEarnedPoints 計算
 * 5. 停用會員（m-5 新增）：綁定會員（resolveMemberSnapshot）、扣點（deductPoints）、
 *    試算點數檢查（ensureSufficientPoints）一律拒絕停用的會員（DomainException，HTTP 422）。
 *    原本 UserService.getMember 只檢查會員資料存在，Member.consumePoints 也不檢查啟用狀態，
 *    停用的會員仍可在結帳時折抵點數。
 * ============================================================
 */
@Component
public class OrderCheckoutService {

    /** 品牌未設定積點規則時的預設值（與 BrandPointConfigEntity 欄位預設一致） */
    private static final BigDecimal DEFAULT_EARN_RATE = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_REDEEM_RATE = new BigDecimal("0.1");

    private final PromotionService promotionService;
    private final BrandPointConfigJpaRepository brandPointConfigJpaRepository;
    private final MemberRepository memberRepository;
    private final UserService userService;
    // Domain Service 無狀態、不依賴框架，直接建立即可
    private final OrderPricingDomainService orderPricingDomainService = new OrderPricingDomainService();

    public OrderCheckoutService(PromotionService promotionService,
                                BrandPointConfigJpaRepository brandPointConfigJpaRepository,
                                MemberRepository memberRepository,
                                UserService userService) {
        this.promotionService = promotionService;
        this.brandPointConfigJpaRepository = brandPointConfigJpaRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    /**
     * 金額計算結果。
     *
     * @param totalAmount       原價總額
     * @param promotionDiscount 促銷折扣（≤ totalAmount）
     * @param pointsDiscount    點數折抵（≤ totalAmount - promotionDiscount）
     * @param pointsUsed        實際使用點數
     */
    public record PricingResult(Money totalAmount, Money promotionDiscount, Money pointsDiscount, long pointsUsed) {

        /** 總折扣 = 促銷 + 點數 */
        public Money totalDiscount() {
            return promotionDiscount.add(pointsDiscount);
        }

        /** 應付金額 = 原價 - 總折扣（Money.subtract 保證最低為 0） */
        public Money finalAmount() {
            return totalAmount.subtract(totalDiscount());
        }
    }

    /**
     * 計算促銷折扣與點數折抵（不扣點、不寫入資料庫）。
     *
     * @param brandId     品牌 ID
     * @param items       訂單品項（Domain）
     * @param totalAmount 原價總額（Order.getTotalAmount()）
     * @param memberId    會員 ID（使用點數時必填）
     * @param pointsToUse 欲使用點數（null 視為 0）
     * @return 計算結果
     * @throws BadRequestException 點數為負數，或使用點數卻沒有指定會員
     */
    public PricingResult calculate(Long brandId, List<OrderItem> items, Money totalAmount,
                                   Long memberId, Long pointsToUse) {
        long requestedPoints = pointsToUse != null ? pointsToUse : 0L;
        if (requestedPoints < 0) {
            throw new BadRequestException("使用點數不可為負數");
        }
        if (requestedPoints > 0 && memberId == null) {
            throw new BadRequestException("使用點數折抵時必須指定會員");
        }

        // 1. 促銷折扣（先套用促銷，再以剩餘金額計算點數折抵）
        Money promotionDiscount = calculatePromotionDiscount(brandId, items, totalAmount);
        Money remaining = totalAmount.subtract(promotionDiscount);

        // 2. 點數折抵：上限為剩餘金額，超出的點數不扣（無條件捨去成整數點）
        long pointsUsed = 0L;
        Money pointsDiscount = Money.ZERO;
        if (requestedPoints > 0) {
            BigDecimal redeemRate = getRedeemRate(brandId);
            if (redeemRate.signum() > 0) {
                long maxUsablePoints = remaining.amount()
                        .divide(redeemRate, 0, RoundingMode.FLOOR)
                        .longValue();
                pointsUsed = Math.min(requestedPoints, maxUsablePoints);
                pointsDiscount = orderPricingDomainService.calculateRedeemAmount(pointsUsed, redeemRate);
                // 防呆：理論上不會超過剩餘金額（floor 計算），仍確保總折扣不超過原價
                if (pointsDiscount.isGreaterThan(remaining)) {
                    pointsDiscount = remaining;
                }
            }
        }
        return new PricingResult(totalAmount, promotionDiscount, pointsDiscount, pointsUsed);
    }

    /**
     * 在「呼叫端的交易內」同步扣除會員點數。
     *
     * 必須在結帳 Handler 的 @Transactional 方法中呼叫：扣點失敗（點數不足）時拋出 DomainException（422），
     * 整筆交易回滾，訂單不會建立；併發扣點由 MemberProfileEntity 的 @Version 樂觀鎖保護（409）。
     *
     * @param brandId  品牌 ID
     * @param memberId 會員 ID
     * @param points   扣除點數（≤ 0 時不做任何事）
     * @param reason   扣點原因（寫入點數異動紀錄）
     * @throws DomainException 點數不足，或會員已停用（HTTP 422）
     */
    public void deductPoints(Long brandId, Long memberId, long points, String reason) {
        if (points <= 0) {
            return;
        }
        Member member = memberRepository.findById(brandId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + memberId));
        // 新增（m-5）：停用的會員不可折抵點數（Member.consumePoints 本身不檢查啟用狀態）
        ensureActive(member.isActive(), memberId);
        try {
            member.consumePoints(points, reason);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Member.consumePoints 以 IllegalStateException 表示點數不足，轉為業務例外（HTTP 422）
            throw new DomainException(e.getMessage());
        }
        memberRepository.save(member);
    }

    /**
     * 檢查會員點數是否足夠（金額試算用，不扣點）。
     *
     * @throws DomainException 點數不足，或會員已停用（HTTP 422）
     */
    public void ensureSufficientPoints(Long brandId, Long memberId, long points) {
        if (points <= 0) {
            return;
        }
        Member member = memberRepository.findById(brandId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + memberId));
        // 新增（m-5）：試算與實際結帳一致，停用的會員不可使用點數
        ensureActive(member.isActive(), memberId);
        long balance = member.getPoints() != null && member.getPoints().value() != null
                ? member.getPoints().value() : 0L;
        if (balance < points) {
            throw new DomainException("會員積點不足，現有 " + balance + " 點，需要 " + points + " 點");
        }
    }

    /**
     * 建立會員快照（同時驗證該使用者確實是本品牌的會員，且帳號未停用）。
     *
     * @param brandId  品牌 ID
     * @param memberId 會員 ID（null 時回傳 null）
     * @return 會員快照
     * @throws ResourceNotFoundException 找不到會員或該使用者不是會員
     * @throws DomainException           會員已停用（HTTP 422）
     */
    public MemberSnapshot resolveMemberSnapshot(Long brandId, Long memberId) {
        if (memberId == null) {
            return null;
        }
        UserEntity memberUser = userService.getMember(brandId, memberId);
        // 新增（m-5）：停用的會員不可綁定新訂單（UserService.getMember 只檢查會員資料存在）
        ensureActive(Boolean.TRUE.equals(memberUser.getIsActive()), memberId);
        return new MemberSnapshot(
                memberUser.getId(),
                memberUser.getMemberProfile().getFullName(),
                memberUser.getPrimaryPhone(),
                memberUser.getMemberProfile().getEmail()
        );
    }

    /**
     * 計算訂單完成（CLOSED）時應累積的點數。
     * 修改（B-2）：原本固定 close(0L)，會員永遠不會累點。
     *
     * @param brandId     品牌 ID
     * @param memberId    會員 ID（null 代表訪客，回傳 0）
     * @param finalAmount 最終實付金額
     * @return 應累積點數
     */
    public long calculateEarnedPoints(Long brandId, Long memberId, Money finalAmount) {
        if (memberId == null) {
            return 0L;
        }
        Long points = orderPricingDomainService.calculateEarnedPoints(finalAmount, getEarnRate(brandId));
        return points != null ? points : 0L;
    }

    // ─── 私有輔助方法 ─────────────────────────────────────────────────────

    /**
     * 新增（m-5）：會員必須為啟用狀態，否則拋出 DomainException（HTTP 422），
     * 呼叫端的結帳交易整筆回滾（不建立訂單、不扣點）。
     */
    private static void ensureActive(boolean active, Long memberId) {
        if (!active) {
            throw new DomainException("會員帳號已停用，無法綁定訂單或使用點數，ID：" + memberId);
        }
    }

    /**
     * 呼叫促銷模組計算最佳折扣（與 JPA Entity 解耦，改傳 PromotionContext）。
     * 折扣上限為訂單總額。
     */
    private Money calculatePromotionDiscount(Long brandId, List<OrderItem> items, Money totalAmount) {
        if (items == null || items.isEmpty()) {
            return Money.ZERO;
        }
        List<PromotionContext.Line> lines = items.stream()
                .map(item -> new PromotionContext.Line(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice() != null ? item.getUnitPrice().amount() : BigDecimal.ZERO,
                        item.getSubtotal() != null ? item.getSubtotal().amount() : BigDecimal.ZERO))
                .toList();
        BigDecimal discount = promotionService.calculateBestDiscount(
                new PromotionContext(brandId, lines, totalAmount.amount()));
        if (discount == null || discount.signum() <= 0) {
            return Money.ZERO;
        }
        Money promotionDiscount = Money.of(discount);
        return promotionDiscount.isGreaterThan(totalAmount) ? totalAmount : promotionDiscount;
    }

    private BigDecimal getRedeemRate(Long brandId) {
        return brandPointConfigJpaRepository.findById(brandId)
                .map(BrandPointConfigEntity::getRedeemRate)
                .orElse(DEFAULT_REDEEM_RATE);
    }

    private BigDecimal getEarnRate(Long brandId) {
        return brandPointConfigJpaRepository.findById(brandId)
                .map(BrandPointConfigEntity::getEarnRate)
                .orElse(DEFAULT_EARN_RATE);
    }
}
