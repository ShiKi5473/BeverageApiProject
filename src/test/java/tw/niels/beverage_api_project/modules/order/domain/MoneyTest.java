package tw.niels.beverage_api_project.modules.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;
import tw.niels.beverage_api_project.modules.order.domain.model.Money;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money 值物件單元測試
 *
 * 測試範圍：
 * - 建立驗證（非負數、null 保護）
 * - 算術運算（加、減、乘）
 * - 邊界情境（折扣超過原價歸零）
 * - 比較方法
 *
 * 注意：此為純粹的 POJO 單元測試，不需要 Spring Context，執行速度極快。
 */
@DisplayName("Money 值物件測試")
class MoneyTest {

    @Nested
    @DisplayName("建立驗證")
    class Creation {

        @Test
        @DisplayName("正常金額可以成功建立")
        void shouldCreateMoneyWithPositiveAmount() {
            // Given / When
            Money money = Money.of(new BigDecimal("100.00"));

            // Then
            assertThat(money.amount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("零元可以成功建立")
        void shouldCreateMoneyWithZeroAmount() {
            Money money = Money.of(BigDecimal.ZERO);
            assertThat(money.amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("小數點超過 2 位時自動四捨五入")
        void shouldRoundToTwoDecimalPlaces() {
            // 150.555 → 四捨五入 → 150.56
            Money money = Money.of(new BigDecimal("150.555"));
            assertThat(money.amount()).isEqualByComparingTo("150.56");
        }

        @Test
        @DisplayName("負數金額應拋出 OrderDomainException")
        void shouldThrowExceptionForNegativeAmount() {
            assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01")))
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("金額不得為負數");
        }

        @Test
        @DisplayName("null 金額應拋出 OrderDomainException")
        void shouldThrowExceptionForNullAmount() {
            assertThatThrownBy(() -> new Money(null))
                    .isInstanceOf(OrderDomainException.class)
                    .hasMessageContaining("金額不得為 null");
        }

        @Test
        @DisplayName("從 long 整數建立 Money")
        void shouldCreateMoneyFromLong() {
            Money money = Money.of(100L);
            assertThat(money.amount()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("加法運算")
    class Addition {

        @Test
        @DisplayName("兩個正數金額相加")
        void shouldAddTwoPositiveAmounts() {
            // Given
            Money a = Money.of(new BigDecimal("100.00"));
            Money b = Money.of(new BigDecimal("50.50"));

            // When
            Money result = a.add(b);

            // Then
            assertThat(result.amount()).isEqualByComparingTo("150.50");
        }

        @Test
        @DisplayName("與 ZERO 相加結果不變")
        void shouldReturnSameAmountWhenAddingZero() {
            Money money = Money.of(new BigDecimal("99.99"));
            Money result = money.add(Money.ZERO);
            assertThat(result.amount()).isEqualByComparingTo("99.99");
        }
    }

    @Nested
    @DisplayName("減法運算")
    class Subtraction {

        @Test
        @DisplayName("正常減法計算正確")
        void shouldSubtractCorrectly() {
            // Given
            Money price = Money.of(new BigDecimal("200.00"));
            Money discount = Money.of(new BigDecimal("30.00"));

            // When
            Money result = price.subtract(discount);

            // Then
            assertThat(result.amount()).isEqualByComparingTo("170.00");
        }

        @Test
        @DisplayName("折扣超過原價時結果歸零（不得為負）")
        void shouldReturnZeroWhenDiscountExceedsPrice() {
            // Given - 促銷折扣超過原價的情境
            Money price = Money.of(new BigDecimal("50.00"));
            Money hugeDiscount = Money.of(new BigDecimal("100.00"));

            // When
            Money result = price.subtract(hugeDiscount);

            // Then - 歸零而非負數
            assertThat(result.amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("減去零結果不變")
        void shouldReturnSameAmountWhenSubtractingZero() {
            Money money = Money.of(new BigDecimal("80.00"));
            Money result = money.subtract(Money.ZERO);
            assertThat(result.amount()).isEqualByComparingTo("80.00");
        }
    }

    @Nested
    @DisplayName("乘法運算")
    class Multiplication {

        @Test
        @DisplayName("金額乘以數量計算小計")
        void shouldMultiplyAmountByQuantity() {
            // Given - 單價 35 元 × 3 杯
            Money unitPrice = Money.of(new BigDecimal("35.00"));

            // When
            Money subtotal = unitPrice.multiply(new BigDecimal("3"));

            // Then
            assertThat(subtotal.amount()).isEqualByComparingTo("105.00");
        }

        @Test
        @DisplayName("金額乘以折扣比例（百分比折扣）")
        void shouldMultiplyByDiscountRate() {
            // Given - 原價 100 元，9 折
            Money price = Money.of(new BigDecimal("100.00"));

            // When - 折扣金額 = 100 × 0.1 = 10 元
            Money discountAmount = price.multiply(new BigDecimal("0.10"));

            // Then
            assertThat(discountAmount.amount()).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    @DisplayName("比較運算")
    class Comparison {

        @Test
        @DisplayName("相同金額 isEqualTo 應回傳 true（忽略 scale 差異）")
        void shouldBeEqualForSameAmount() {
            // BigDecimal equals 對 scale 敏感（1.0 ≠ 1.00），Money 使用 compareTo 避免此問題
            Money a = Money.of(new BigDecimal("100.0"));
            Money b = Money.of(new BigDecimal("100.00"));
            assertThat(a.isEqualTo(b)).isTrue();
        }

        @Test
        @DisplayName("isGreaterThan 比較大小正確")
        void shouldCompareMagnitudeCorrectly() {
            Money larger = Money.of(new BigDecimal("200.00"));
            Money smaller = Money.of(new BigDecimal("100.00"));

            assertThat(larger.isGreaterThan(smaller)).isTrue();
            assertThat(smaller.isGreaterThan(larger)).isFalse();
        }
    }

    @Nested
    @DisplayName("ZERO 常數")
    class ZeroConstant {

        @Test
        @DisplayName("ZERO 常數金額為 0")
        void shouldHaveZeroAmount() {
            assertThat(Money.ZERO.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("add ZERO 不產生負數")
        void shouldNotProduceNegativeWhenAddingZero() {
            Money result = Money.ZERO.add(Money.of(new BigDecimal("50.00")));
            assertThat(result.amount()).isEqualByComparingTo("50.00");
        }
    }
}
