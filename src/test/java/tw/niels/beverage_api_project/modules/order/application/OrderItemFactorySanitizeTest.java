package tw.niels.beverage_api_project.modules.order.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tw.niels.beverage_api_project.modules.order.application.service.OrderItemFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderItemFactory.sanitize 單元測試（新增，m-3 備註改存純文字）。
 *
 * 原本以 Jsoup.clean(text, Safelist.none()) 清洗，輸出為 HTML：
 * "少冰 & 半糖" 會存成 "少冰 &amp; 半糖"、換行被壓掉，前端以純文字顯示時會看到實體字元。
 * 現在只移除標籤、不跳脫實體，並保留換行、去除前後空白，空字串回傳 null。
 */
@DisplayName("OrderItemFactory.sanitize 備註純文字化")
class OrderItemFactorySanitizeTest {

    @Test
    @DisplayName("& 等符號維持原樣，不會被跳脫成 &amp;")
    void shouldNotEscapeEntities() {
        assertThat(OrderItemFactory.sanitize("少冰 & 半糖")).isEqualTo("少冰 & 半糖");
        assertThat(OrderItemFactory.sanitize("1 < 2 > 0")).isEqualTo("1 < 2 > 0");
        assertThat(OrderItemFactory.sanitize("少冰<半糖")).isEqualTo("少冰<半糖");
    }

    @Test
    @DisplayName("移除 HTML 標籤但保留內文；script 內容整段丟棄")
    void shouldStripTags() {
        assertThat(OrderItemFactory.sanitize("<b>x</b>")).isEqualTo("x");
        assertThat(OrderItemFactory.sanitize("<script>alert(1)</script>少冰")).isEqualTo("少冰");
        assertThat(OrderItemFactory.sanitize("<img src=x onerror=alert(1)>去冰")).isEqualTo("去冰");
    }

    @Test
    @DisplayName("保留換行（統一為 \\n），<br> 視為換行，過多空行壓成一個空行")
    void shouldKeepLineBreaks() {
        assertThat(OrderItemFactory.sanitize("第一行\n第二行")).isEqualTo("第一行\n第二行");
        assertThat(OrderItemFactory.sanitize("第一行\r\n第二行")).isEqualTo("第一行\n第二行");
        assertThat(OrderItemFactory.sanitize("a<br>b")).isEqualTo("a\nb");
        assertThat(OrderItemFactory.sanitize("a\n\n\n\nb")).isEqualTo("a\n\nb");
    }

    @Test
    @DisplayName("去除前後空白（含全形空白）；空白或只有標籤時回傳 null")
    void shouldTrimAndReturnNullWhenEmpty() {
        assertThat(OrderItemFactory.sanitize("  少冰  \n")).isEqualTo("少冰");
        assertThat(OrderItemFactory.sanitize("　半糖　")).isEqualTo("半糖");
        assertThat(OrderItemFactory.sanitize("   ")).isNull();
        assertThat(OrderItemFactory.sanitize("<p></p>")).isNull();
        assertThat(OrderItemFactory.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("實體編碼的標籤解碼後也會被移除，且結果重複清洗不再改變（冪等）")
    void shouldBeIdempotent() {
        assertThat(OrderItemFactory.sanitize("&lt;b&gt;x&lt;/b&gt;")).isEqualTo("x");
        String once = OrderItemFactory.sanitize("少冰 & 半糖\n<i>加珍珠</i>");
        assertThat(once).isEqualTo("少冰 & 半糖\n加珍珠");
        assertThat(OrderItemFactory.sanitize(once)).isEqualTo(once);
    }
}
