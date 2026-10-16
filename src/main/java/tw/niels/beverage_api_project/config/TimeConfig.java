package tw.niels.beverage_api_project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 統一時區設定。
 * <p>
 * 營業日（訂單流水號重置、日結報表日界）必須以店家所在時區計算，
 * 不可依賴 JVM 預設時區（Docker 映像預設為 UTC，會造成 8 小時偏移）。
 * 需要「今天」的地方請注入 {@link Clock}，使用 {@code LocalDate.now(clock)}。
 * </p>
 */
@Configuration
public class TimeConfig {

    /** 營業時區，預設台北，可由 app.timezone 覆寫 */
    @Bean
    public ZoneId businessZoneId(@Value("${app.timezone:Asia/Taipei}") String timezone) {
        return ZoneId.of(timezone);
    }

    /** 以營業時區建立的系統時鐘（測試時可替換為固定時鐘） */
    @Bean
    public Clock clock(ZoneId businessZoneId) {
        return Clock.system(businessZoneId);
    }
}
