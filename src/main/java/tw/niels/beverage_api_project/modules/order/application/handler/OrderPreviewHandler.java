package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.domain.vo.Money;
import tw.niels.beverage_api_project.modules.order.application.query.PreviewOrderQuery;
import tw.niels.beverage_api_project.modules.order.application.service.OrderCheckoutService;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;
import tw.niels.beverage_api_project.modules.order.domain.model.OrderItem;
import tw.niels.beverage_api_project.modules.order.dto.OrderPreviewResponseDto;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

import java.util.List;

/**
 * 訂單金額試算 Handler（新增，B-8 前後端金額不一致）
 *
 * 前端原本自行用 product.basePrice 與寫死的點數比率計算金額，與後端（規格價、選項加價、
 * 品牌 redeemRate、促銷）不一致。此 Handler 重用結帳時完全相同的元件計算金額，但不寫入資料庫：
 * - OrderItemFactory：規格價 + 選項加價
 * - OrderCheckoutService：促銷折扣、點數折抵（上限為扣除促銷後的金額）
 * - 點數不足時回 422（與實際結帳行為一致，讓前端提早得知）
 * - 停用的會員回 422（m-5 新增，與實際結帳行為一致）
 */
@Component
public class OrderPreviewHandler {

    private final OrderItemFactory orderItemFactory;
    private final OrderCheckoutService orderCheckoutService;
    private final StoreService storeService;

    public OrderPreviewHandler(OrderItemFactory orderItemFactory,
                               OrderCheckoutService orderCheckoutService,
                               StoreService storeService) {
        this.orderItemFactory = orderItemFactory;
        this.orderCheckoutService = orderCheckoutService;
        this.storeService = storeService;
    }

    /**
     * 試算訂單金額（唯讀，不建立訂單、不扣點）。
     *
     * @param query 試算查詢
     * @return 試算結果
     */
    @Transactional(readOnly = true)
    public OrderPreviewResponseDto handle(PreviewOrderQuery query) {
        // 有指定分店時驗證分店屬於本品牌
        if (query.storeId() != null) {
            storeService.getStore(query.brandId(), query.storeId());
        }
        // 新增（m-5）：有指定會員時與實際結帳相同驗證會員（存在 → 否則 404；已停用 → 422），
        // 讓前端在試算階段就得知停用會員不能綁定 / 折抵點數
        if (query.memberId() != null) {
            orderCheckoutService.resolveMemberSnapshot(query.brandId(), query.memberId());
        }

        // 1. 與結帳相同的品項計算（規格價 + 選項加價，含規格 / 選項歸屬驗證）
        List<OrderItem> items = orderItemFactory.createItems(query.brandId(), query.items());
        Money totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, Money::add);

        // 2. 促銷與點數折抵
        OrderCheckoutService.PricingResult pricing = orderCheckoutService.calculate(
                query.brandId(), items, totalAmount, query.memberId(), query.pointsToUse());

        // 3. 點數不足時回 422（只檢查，不扣點）
        if (pricing.pointsUsed() > 0) {
            orderCheckoutService.ensureSufficientPoints(query.brandId(), query.memberId(), pricing.pointsUsed());
        }

        return new OrderPreviewResponseDto(
                pricing.totalAmount().amount(),
                pricing.promotionDiscount().amount(),
                pricing.pointsDiscount().amount(),
                pricing.pointsUsed(),
                pricing.finalAmount().amount()
        );
    }
}
