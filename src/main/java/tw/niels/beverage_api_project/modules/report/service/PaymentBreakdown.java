package tw.niels.beverage_api_project.modules.report.service;

import java.math.BigDecimal;

/**
 * 付款方式金額細分（報表用值物件）。
 *
 * <p>[B-6] 系統實際的付款方式代碼為 CASH / CREDIT_CARD / THIRD_PARTY（見 V8__insert_payment_methods.sql），
 * 沒有付款方式的訂單（payment_method_id 為 NULL）或日後新增、尚未列入細分的代碼，一律歸入「未指定」，
 * 確保四個欄位加總永遠等於實收金額。批次日結（StoreSettlementService）與即時統計
 * （OrderClosedReportHandler）共用這份對應規則，避免兩邊各寫一套而分歧。</p>
 *
 * @param cash        現金
 * @param creditCard  信用卡
 * @param thirdParty  第三方支付
 * @param unspecified 未指定付款方式
 */
public record PaymentBreakdown(BigDecimal cash,
                               BigDecimal creditCard,
                               BigDecimal thirdParty,
                               BigDecimal unspecified) {

    public static final String CODE_CASH = "CASH";
    public static final String CODE_CREDIT_CARD = "CREDIT_CARD";
    public static final String CODE_THIRD_PARTY = "THIRD_PARTY";

    /** 全部為 0 的細分（累加的起點） */
    public static PaymentBreakdown empty() {
        return new PaymentBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 依付款方式代碼，把一筆金額放進對應的欄位，其餘欄位為 0。
     *
     * @param paymentCode 付款方式代碼（可為 null，代表沒有付款方式；不分大小寫）
     * @param amount      金額（null 視為 0）
     */
    public static PaymentBreakdown of(String paymentCode, BigDecimal amount) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        if (paymentCode != null) {
            if (CODE_CASH.equalsIgnoreCase(paymentCode)) {
                return new PaymentBreakdown(value, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            if (CODE_CREDIT_CARD.equalsIgnoreCase(paymentCode)) {
                return new PaymentBreakdown(BigDecimal.ZERO, value, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            if (CODE_THIRD_PARTY.equalsIgnoreCase(paymentCode)) {
                return new PaymentBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, value, BigDecimal.ZERO);
            }
        }
        // null 或未知代碼 → 未指定
        return new PaymentBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, value);
    }

    /** 逐欄位相加，回傳新的細分（不可變） */
    public PaymentBreakdown plus(PaymentBreakdown other) {
        return new PaymentBreakdown(
                cash.add(other.cash),
                creditCard.add(other.creditCard),
                thirdParty.add(other.thirdParty),
                unspecified.add(other.unspecified));
    }
}
