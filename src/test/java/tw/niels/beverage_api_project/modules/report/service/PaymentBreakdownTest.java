package tw.niels.beverage_api_project.modules.report.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [B-6] 付款方式細分對應規則：CASH / CREDIT_CARD / THIRD_PARTY 各自歸欄，null 或未知代碼歸入「未指定」。
 */
class PaymentBreakdownTest {

    private static final BigDecimal AMOUNT = new BigDecimal("80");

    @Test
    @DisplayName("已知代碼 (不分大小寫) 歸入對應欄位")
    void knownCodes() {
        PaymentBreakdown cash = PaymentBreakdown.of("cash", AMOUNT);
        assertThat(cash.cash()).isEqualByComparingTo("80");
        assertThat(cash.unspecified()).isEqualByComparingTo("0");

        assertThat(PaymentBreakdown.of("CREDIT_CARD", AMOUNT).creditCard()).isEqualByComparingTo("80");
        assertThat(PaymentBreakdown.of("THIRD_PARTY", AMOUNT).thirdParty()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("null 或未知代碼歸入未指定")
    void unspecified() {
        assertThat(PaymentBreakdown.of(null, AMOUNT).unspecified()).isEqualByComparingTo("80");
        assertThat(PaymentBreakdown.of("LINE_PAY", AMOUNT).unspecified()).isEqualByComparingTo("80");
        assertThat(PaymentBreakdown.of("CASH", null).cash()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("plus 逐欄位相加，四欄加總等於總額")
    void plus() {
        PaymentBreakdown total = PaymentBreakdown.empty()
                .plus(PaymentBreakdown.of("CASH", new BigDecimal("50")))
                .plus(PaymentBreakdown.of("CASH", new BigDecimal("50")))
                .plus(PaymentBreakdown.of("CREDIT_CARD", new BigDecimal("80")))
                .plus(PaymentBreakdown.of(null, new BigDecimal("30")));

        assertThat(total.cash()).isEqualByComparingTo("100");
        assertThat(total.creditCard()).isEqualByComparingTo("80");
        assertThat(total.thirdParty()).isEqualByComparingTo("0");
        assertThat(total.unspecified()).isEqualByComparingTo("30");
    }
}
